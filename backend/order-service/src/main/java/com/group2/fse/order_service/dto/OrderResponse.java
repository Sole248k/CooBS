package com.group2.fse.order_service.dto;

import com.group2.fse.order_service.entity.OrderStage;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long orderId;
    private String referenceNo;
    private Long customerId;
    private Long sourceAccountId;
    private Long holdId;
    private OrderStage stage;
    private BigDecimal totalAmount;
    private String pickupBranch;
    private String pickupPassToken;
    private String pickupTimeSlot;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
