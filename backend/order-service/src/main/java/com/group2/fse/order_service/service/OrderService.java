package com.group2.fse.order_service.service;

import com.group2.fse.order_service.dto.*;

import java.util.List;

public interface OrderService {

    List<ProductResponse> getProducts();

    OrderResponse createOrder(Long customerId, CreateOrderRequest request, String idempotencyKey);

    List<OrderResponse> getMyOrders(Long customerId);

    OrderResponse getOrderByReference(Long customerId, String referenceNo);

    OrderResponse cancelOrder(Long customerId, String referenceNo);

    OrderResponse prepareOrder(String referenceNo);

    OrderResponse markReady(String referenceNo);

    OrderResponse collectOrder(String referenceNo, Long actorId);

    PickupVerificationResponse verifyPickupPass(String pickupPassToken);
}
