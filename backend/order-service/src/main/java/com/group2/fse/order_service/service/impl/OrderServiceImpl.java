package com.group2.fse.order_service.service.impl;

import com.group2.fse.order_service.client.LedgerHoldClient;
import com.group2.fse.order_service.client.dto.*;
import com.group2.fse.order_service.dto.*;
import com.group2.fse.order_service.entity.*;
import com.group2.fse.order_service.exception.InsufficientStockException;
import com.group2.fse.order_service.exception.OrderInvalidStageException;
import com.group2.fse.order_service.exception.OrderNotFoundException;
import com.group2.fse.order_service.repository.ShopOrderRepository;
import com.group2.fse.order_service.repository.ShopProductRepository;
import com.group2.fse.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ShopProductRepository productRepository;
    private final ShopOrderRepository orderRepository;
    private final LedgerHoldClient ledgerHoldClient;

    @Value("${app.shop.default-merchant-account-id:1001}")
    private Long defaultMerchantAccountId;

    @Override
    @Transactional
    public List<ProductResponse> getProducts() {
        List<ShopProduct> products = productRepository.findByIsActiveOrderByProductIdAsc(1);
        if (products.isEmpty()) {
            log.info("Seeding initial CooBS essentials depot catalogue products...");
            seedDefaultProducts();
            products = productRepository.findByIsActiveOrderByProductIdAsc(1);
        }
        return products.stream().map(this::mapProduct).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderResponse createOrder(Long customerId, CreateOrderRequest request, String idempotencyKey) {
        log.info("Processing shop order for customerId={}, accountId={}", customerId, request.getSourceAccountId());

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<ShopOrderItem> items = new ArrayList<>();

        for (OrderItemDto itemDto : request.getItems()) {
            ShopProduct product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new OrderNotFoundException("Product ID " + itemDto.getProductId() + " not found"));

            if (product.getStockQuantity() < itemDto.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for product '" + product.getName() + "'. Available: " + product.getStockQuantity());
            }

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            // Deduct inventory
            product.setStockQuantity(product.getStockQuantity() - itemDto.getQuantity());
            productRepository.save(product);

            items.add(ShopOrderItem.builder()
                    .productId(product.getProductId())
                    .productName(product.getName())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .build());
        }

        // Generate compact unique reference (max 34 chars for Ledger placeHold compliance)
        String refSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String referenceNo = "ORD-" + (System.currentTimeMillis() % 10000000000L) + "-" + refSuffix;
        String passToken = "PASS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        // 1. Two-Phase Escrow Reservation via ledger-service
        PlaceHoldRequestDto holdRequest = PlaceHoldRequestDto.builder()
                .referenceNo(referenceNo)
                .accountId(request.getSourceAccountId())
                .amount(totalAmount)
                .currency("PHP")
                .actorId(customerId)
                .build();

        log.info("Reserving balance hold for order {}: amount=₱{}", referenceNo, totalAmount);
        PlaceHoldResponseDto holdResponse = ledgerHoldClient.placeHold(holdRequest, idempotencyKey);

        Long holdId = holdResponse != null ? holdResponse.getHoldId() : null;

        // 2. Persist order in Oracle
        ShopOrder order = ShopOrder.builder()
                .referenceNo(referenceNo)
                .customerId(customerId)
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(defaultMerchantAccountId)
                .holdId(holdId)
                .stage(OrderStage.HELD)
                .totalAmount(totalAmount)
                .pickupBranch(request.getPickupBranch() != null ? request.getPickupBranch() : "PureSilver Central Mall Depot")
                .pickupPassToken(passToken)
                .pickupTimeSlot(request.getPickupTimeSlot() != null ? request.getPickupTimeSlot() : "4:00 PM - 6:00 PM")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        for (ShopOrderItem item : items) {
            item.setOrder(order);
        }
        order.setItems(items);

        ShopOrder saved = orderRepository.save(order);
        log.info("Shop order {} persisted with holdId {}", saved.getReferenceNo(), saved.getHoldId());
        return mapOrder(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(this::mapOrder).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByReference(Long customerId, String referenceNo) {
        ShopOrder order = orderRepository.findByCustomerIdAndReferenceNo(customerId, referenceNo)
                .orElseThrow(() -> new OrderNotFoundException("Order " + referenceNo + " not found for customer"));
        return mapOrder(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long customerId, String referenceNo) {
        ShopOrder order = orderRepository.findByCustomerIdAndReferenceNo(customerId, referenceNo)
                .orElseThrow(() -> new OrderNotFoundException("Order " + referenceNo + " not found for customer"));

        if (order.getStage() != OrderStage.HELD) {
            throw new OrderInvalidStageException("Cannot cancel order in stage '" + order.getStage() + "'. Only HELD orders before preparation can be cancelled.");
        }

        // Release ledger hold if placed
        if (order.getHoldId() != null) {
            log.info("Releasing ledger hold {} for cancelled order {}", order.getHoldId(), referenceNo);
            ReleaseHoldRequestDto releaseRequest = ReleaseHoldRequestDto.builder()
                    .reason("MEMBER_CANCELLED")
                    .build();
            ledgerHoldClient.releaseHold(order.getHoldId(), releaseRequest, null);
        }

        // Restock inventory
        for (ShopOrderItem item : order.getItems()) {
            if (item.getProductId() != null) {
                productRepository.findById(item.getProductId()).ifPresent(prod -> {
                    prod.setStockQuantity(prod.getStockQuantity() + item.getQuantity());
                    productRepository.save(prod);
                });
            }
        }

        order.setStage(OrderStage.CANCELLED);
        ShopOrder updated = orderRepository.save(order);
        return mapOrder(updated);
    }

    @Override
    @Transactional
    public OrderResponse prepareOrder(String referenceNo) {
        ShopOrder order = orderRepository.findByReferenceNo(referenceNo)
                .orElseThrow(() -> new OrderNotFoundException("Order " + referenceNo + " not found"));

        if (order.getStage() != OrderStage.HELD) {
            throw new OrderInvalidStageException("Cannot mark preparing from stage: " + order.getStage());
        }

        order.setStage(OrderStage.PREPARING);
        return mapOrder(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse markReady(String referenceNo) {
        ShopOrder order = orderRepository.findByReferenceNo(referenceNo)
                .orElseThrow(() -> new OrderNotFoundException("Order " + referenceNo + " not found"));

        if (order.getStage() != OrderStage.PREPARING && order.getStage() != OrderStage.HELD) {
            throw new OrderInvalidStageException("Cannot mark ready from stage: " + order.getStage());
        }

        order.setStage(OrderStage.READY);
        return mapOrder(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse collectOrder(String referenceNo, Long actorId) {
        ShopOrder order = orderRepository.findByReferenceNo(referenceNo)
                .orElseThrow(() -> new OrderNotFoundException("Order " + referenceNo + " not found"));

        if (order.getStage() == OrderStage.COMPLETED || order.getStage() == OrderStage.CANCELLED) {
            throw new OrderInvalidStageException("Order already " + order.getStage());
        }

        // Capture Two-Phase Escrow Hold into Merchant Account
        if (order.getHoldId() != null) {
            log.info("Capturing ledger hold {} for order pickup {} to merchant account {}",
                    order.getHoldId(), referenceNo, defaultMerchantAccountId);

            CaptureHoldRequestDto captureRequest = CaptureHoldRequestDto.builder()
                    .destinationAccountId(defaultMerchantAccountId)
                    .remarks("PureSilver Depot Collection: " + order.getReferenceNo())
                    .actorId(actorId)
                    .build();
            ledgerHoldClient.captureHold(order.getHoldId(), captureRequest, null);
        }

        order.setStage(OrderStage.COMPLETED);
        return mapOrder(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public PickupVerificationResponse verifyPickupPass(String pickupPassToken) {
        Optional<ShopOrder> opt = orderRepository.findByPickupPassToken(pickupPassToken);
        if (opt.isEmpty()) {
            return PickupVerificationResponse.builder()
                    .valid(false)
                    .message("Digital pickup pass token invalid or not recognized.")
                    .build();
        }

        ShopOrder order = opt.get();
        boolean isValid = order.getStage() == OrderStage.READY || order.getStage() == OrderStage.HELD || order.getStage() == OrderStage.PREPARING;

        return PickupVerificationResponse.builder()
                .valid(isValid)
                .referenceNo(order.getReferenceNo())
                .customerId(order.getCustomerId())
                .stage(order.getStage())
                .totalAmount(order.getTotalAmount())
                .pickupBranch(order.getPickupBranch())
                .pickupTimeSlot(order.getPickupTimeSlot())
                .items(order.getItems().stream().map(this::mapItem).collect(Collectors.toList()))
                .message(isValid ? "Pickup pass is valid for collection." : "Order is already " + order.getStage())
                .build();
    }

    private void seedDefaultProducts() {
        productRepository.save(ShopProduct.builder()
                .sku("RICE-SINANDOMENG-5KG")
                .name("Premium rice")
                .category("grains")
                .unit("5kg sack")
                .price(new BigDecimal("320.0000"))
                .originalPrice(new BigDecimal("380.0000"))
                .badge("ZERO_MARKUP")
                .farmSource("Nueva Ecija Agrarian Reform Co-op")
                .stockQuantity(500)
                .isActive(1)
                .build());

        productRepository.save(ShopProduct.builder()
                .sku("EGGS-BATANGAS-30S")
                .name("Fresh farm eggs")
                .category("poultry")
                .unit("Tray of 30")
                .price(new BigDecimal("175.0000"))
                .originalPrice(new BigDecimal("210.0000"))
                .badge("FARM_FRESH")
                .farmSource("Batangas Layer Farmers Cooperative")
                .stockQuantity(300)
                .isActive(1)
                .build());

        productRepository.save(ShopProduct.builder()
                .sku("MILK-LIPA-1L")
                .name("Pure fresh milk")
                .category("dairy")
                .unit("1 liter bottle")
                .price(new BigDecimal("40.0000"))
                .originalPrice(new BigDecimal("55.0000"))
                .badge("COOP_DIRECT")
                .farmSource("Lipa Dairy Producers Cooperative")
                .stockQuantity(200)
                .isActive(1)
                .build());
    }

    private ProductResponse mapProduct(ShopProduct p) {
        return ProductResponse.builder()
                .productId(p.getProductId())
                .sku(p.getSku())
                .name(p.getName())
                .category(p.getCategory())
                .unit(p.getUnit())
                .price(p.getPrice())
                .originalPrice(p.getOriginalPrice())
                .badge(p.getBadge())
                .farmSource(p.getFarmSource())
                .stockQuantity(p.getStockQuantity())
                .build();
    }

    private OrderResponse mapOrder(ShopOrder o) {
        return OrderResponse.builder()
                .orderId(o.getOrderId())
                .referenceNo(o.getReferenceNo())
                .customerId(o.getCustomerId())
                .sourceAccountId(o.getSourceAccountId())
                .holdId(o.getHoldId())
                .stage(o.getStage())
                .totalAmount(o.getTotalAmount())
                .pickupBranch(o.getPickupBranch())
                .pickupPassToken(o.getPickupPassToken())
                .pickupTimeSlot(o.getPickupTimeSlot())
                .items(o.getItems() != null ? o.getItems().stream().map(this::mapItem).collect(Collectors.toList()) : List.of())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }

    private OrderItemResponse mapItem(ShopOrderItem i) {
        return OrderItemResponse.builder()
                .itemId(i.getItemId())
                .productId(i.getProductId())
                .productName(i.getProductName())
                .quantity(i.getQuantity())
                .unitPrice(i.getUnitPrice())
                .subtotal(i.getSubtotal())
                .build();
    }
}
