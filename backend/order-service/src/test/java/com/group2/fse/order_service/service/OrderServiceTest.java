package com.group2.fse.order_service.service;

import com.group2.fse.order_service.client.LedgerHoldClient;
import com.group2.fse.order_service.client.dto.*;
import com.group2.fse.order_service.dto.*;
import com.group2.fse.order_service.entity.*;
import com.group2.fse.order_service.exception.OrderInvalidStageException;
import com.group2.fse.order_service.exception.OrderNotFoundException;
import com.group2.fse.order_service.repository.ShopOrderRepository;
import com.group2.fse.order_service.repository.ShopProductRepository;
import com.group2.fse.order_service.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private ShopProductRepository productRepository;

    @Mock
    private ShopOrderRepository orderRepository;

    @Mock
    private LedgerHoldClient ledgerHoldClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private ShopProduct riceProduct;
    private ShopProduct eggsProduct;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "defaultMerchantAccountId", 1001L);

        riceProduct = ShopProduct.builder()
                .productId(101L)
                .sku("RICE-5KG")
                .name("Premium rice")
                .category("grains")
                .unit("5kg sack")
                .price(new BigDecimal("320.0000"))
                .stockQuantity(100)
                .isActive(1)
                .build();

        eggsProduct = ShopProduct.builder()
                .productId(102L)
                .sku("EGGS-30S")
                .name("Fresh farm eggs")
                .category("poultry")
                .unit("Tray of 30")
                .price(new BigDecimal("175.0000"))
                .stockQuantity(50)
                .isActive(1)
                .build();
    }

    @Test
    void getProducts_returnsActiveProducts() {
        when(productRepository.findByIsActiveOrderByProductIdAsc(1))
                .thenReturn(List.of(riceProduct, eggsProduct));

        List<ProductResponse> products = orderService.getProducts();

        assertThat(products).hasSize(2);
        assertThat(products.get(0).getName()).isEqualTo("Premium rice");
        assertThat(products.get(1).getName()).isEqualTo("Fresh farm eggs");
    }

    @Test
    void createOrder_placesLedgerHoldAndSavesOrder() {
        when(productRepository.findById(101L)).thenReturn(Optional.of(riceProduct));
        when(productRepository.findById(102L)).thenReturn(Optional.of(eggsProduct));

        PlaceHoldResponseDto holdResponse = PlaceHoldResponseDto.builder()
                .holdId(9001L)
                .referenceNo("ORD-TEST-123")
                .status("RESERVED")
                .amount(new BigDecimal("495.0000"))
                .build();

        when(ledgerHoldClient.placeHold(any(PlaceHoldRequestDto.class), any()))
                .thenReturn(holdResponse);

        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(inv -> {
                    ShopOrder saved = inv.getArgument(0);
                    saved.setOrderId(501L);
                    return saved;
                });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .sourceAccountId(1002L)
                .items(List.of(
                        OrderItemDto.builder().productId(101L).quantity(1).build(),
                        OrderItemDto.builder().productId(102L).quantity(1).build()
                ))
                .build();

        OrderResponse response = orderService.createOrder(1000L, request, "idemp-key-1");

        assertThat(response).isNotNull();
        assertThat(response.getHoldId()).isEqualTo(9001L);
        assertThat(response.getStage()).isEqualTo(OrderStage.HELD);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("495.0000"));

        verify(ledgerHoldClient, times(1)).placeHold(any(PlaceHoldRequestDto.class), eq("idemp-key-1"));
        verify(orderRepository, times(1)).save(any(ShopOrder.class));
    }

    @Test
    void cancelOrder_inHeldStage_releasesLedgerHold() {
        ShopOrder order = ShopOrder.builder()
                .orderId(501L)
                .referenceNo("ORD-CANCEL-1")
                .customerId(1000L)
                .holdId(9001L)
                .stage(OrderStage.HELD)
                .totalAmount(new BigDecimal("320.0000"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByCustomerIdAndReferenceNo(1000L, "ORD-CANCEL-1"))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrderResponse cancelled = orderService.cancelOrder(1000L, "ORD-CANCEL-1");

        assertThat(cancelled.getStage()).isEqualTo(OrderStage.CANCELLED);
        verify(ledgerHoldClient, times(1)).releaseHold(eq(9001L), any(), isNull());
    }

    @Test
    void cancelOrder_inPreparingStage_throwsConflict() {
        ShopOrder order = ShopOrder.builder()
                .orderId(501L)
                .referenceNo("ORD-PREPARING-1")
                .customerId(1000L)
                .stage(OrderStage.PREPARING)
                .build();

        when(orderRepository.findByCustomerIdAndReferenceNo(1000L, "ORD-PREPARING-1"))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1000L, "ORD-PREPARING-1"))
                .isInstanceOf(OrderInvalidStageException.class)
                .hasMessageContaining("Cannot cancel order in stage 'PREPARING'");

        verify(ledgerHoldClient, never()).releaseHold(any(), any(), any());
    }

    @Test
    void collectOrder_capturesLedgerHoldAndCompletes() {
        ShopOrder order = ShopOrder.builder()
                .orderId(501L)
                .referenceNo("ORD-READY-1")
                .holdId(9001L)
                .stage(OrderStage.READY)
                .destinationAccountId(1001L)
                .build();

        when(orderRepository.findByReferenceNo("ORD-READY-1"))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrderResponse completed = orderService.collectOrder("ORD-READY-1", 999L);

        assertThat(completed.getStage()).isEqualTo(OrderStage.COMPLETED);
        verify(ledgerHoldClient, times(1)).captureHold(eq(9001L), any(CaptureHoldRequestDto.class), isNull());
    }

    @Test
    void verifyPickupPass_validToken_returnsValidResponse() {
        ShopOrder order = ShopOrder.builder()
                .orderId(501L)
                .referenceNo("ORD-PASS-1")
                .customerId(1000L)
                .pickupPassToken("PASS-VALID-123")
                .stage(OrderStage.READY)
                .totalAmount(new BigDecimal("320.0000"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByPickupPassToken("PASS-VALID-123"))
                .thenReturn(Optional.of(order));

        PickupVerificationResponse verification = orderService.verifyPickupPass("PASS-VALID-123");

        assertThat(verification.isValid()).isTrue();
        assertThat(verification.getReferenceNo()).isEqualTo("ORD-PASS-1");
    }
}
