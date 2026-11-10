package com.group2.fse.order_service.controller;

import com.group2.fse.order_service.dto.CreateOrderRequest;
import com.group2.fse.order_service.dto.OrderResponse;
import com.group2.fse.order_service.dto.ProductResponse;
import com.group2.fse.order_service.security.UserPrincipal;
import com.group2.fse.order_service.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/shop")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final OrderService orderService;

    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getProducts() {
        return ResponseEntity.ok(orderService.getProducts());
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long customerId = resolveCustomerId(principal);
        log.info("Received order creation request for customer {}: items={}", customerId, request.getItems().size());
        OrderResponse response = orderService.createOrder(customerId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<List<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal principal) {

        Long customerId = resolveCustomerId(principal);
        return ResponseEntity.ok(orderService.getMyOrders(customerId));
    }

    @GetMapping("/orders/{referenceNo}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable String referenceNo,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long customerId = resolveCustomerId(principal);
        return ResponseEntity.ok(orderService.getOrderByReference(customerId, referenceNo));
    }

    @PostMapping("/orders/{referenceNo}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable String referenceNo,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long customerId = resolveCustomerId(principal);
        log.info("Customer {} cancelling order {}", customerId, referenceNo);
        return ResponseEntity.ok(orderService.cancelOrder(customerId, referenceNo));
    }

    private Long resolveCustomerId(UserPrincipal principal) {
        if (principal != null && principal.getUserId() != null) {
            return principal.getUserId();
        }
        return 1000L;
    }
}
