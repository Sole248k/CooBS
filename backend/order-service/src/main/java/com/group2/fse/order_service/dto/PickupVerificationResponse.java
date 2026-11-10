package com.group2.fse.order_service.dto;

import com.group2.fse.order_service.entity.OrderStage;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickupVerificationResponse {

    private boolean valid;
    private String referenceNo;
    private Long customerId;
    private OrderStage stage;
    private BigDecimal totalAmount;
    private String pickupBranch;
    private String pickupTimeSlot;
    private List<OrderItemResponse> items;
    private String message;
}
