package com.group2.fse.order_service.controller;

import com.group2.fse.order_service.dto.OrderResponse;
import com.group2.fse.order_service.dto.PickupVerificationResponse;
import com.group2.fse.order_service.security.UserPrincipal;
import com.group2.fse.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/shop/depot")
@RequiredArgsConstructor
public class DepotOrderController {

    private final OrderService orderService;

    @GetMapping("/verify/{pickupPassToken}")
    @PreAuthorize("hasAnyRole('TELLER', 'STAFF', 'ADMIN')")
    public ResponseEntity<PickupVerificationResponse> verifyPass(
            @PathVariable String pickupPassToken) {
        log.info("Depot staff verifying pickup pass: {}", pickupPassToken);
        return ResponseEntity.ok(orderService.verifyPickupPass(pickupPassToken));
    }

    @PostMapping("/orders/{referenceNo}/prepare")
    @PreAuthorize("hasAnyRole('TELLER', 'STAFF', 'ADMIN')")
    public ResponseEntity<OrderResponse> prepareOrder(
            @PathVariable String referenceNo) {
        log.info("Depot staff starting basket preparation for order: {}", referenceNo);
        return ResponseEntity.ok(orderService.prepareOrder(referenceNo));
    }

    @PostMapping("/orders/{referenceNo}/ready")
    @PreAuthorize("hasAnyRole('TELLER', 'STAFF', 'ADMIN')")
    public ResponseEntity<OrderResponse> markReady(
            @PathVariable String referenceNo) {
        log.info("Depot staff marked order ready for pickup: {}", referenceNo);
        return ResponseEntity.ok(orderService.markReady(referenceNo));
    }

    @PostMapping("/orders/{referenceNo}/collect")
    @PreAuthorize("hasAnyRole('TELLER', 'STAFF', 'ADMIN')")
    public ResponseEntity<OrderResponse> collectOrder(
            @PathVariable String referenceNo,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long actorId = principal != null ? principal.getUserId() : 999L;
        log.info("Depot staff {} confirming collection and capturing payment for order: {}", actorId, referenceNo);
        return ResponseEntity.ok(orderService.collectOrder(referenceNo, actorId));
    }
}
