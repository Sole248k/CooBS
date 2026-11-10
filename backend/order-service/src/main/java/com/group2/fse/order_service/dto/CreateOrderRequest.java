package com.group2.fse.order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    @NotNull(message = "Source account ID is required")
    private Long sourceAccountId;

    @NotEmpty(message = "Order basket must contain at least one item")
    @Valid
    private List<OrderItemDto> items;

    @Builder.Default
    private String pickupBranch = "PureSilver Central Mall Depot";

    @Builder.Default
    private String pickupTimeSlot = "4:00 PM - 6:00 PM";
}
