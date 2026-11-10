package com.group2.fse.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycRejectRequest {

    @NotBlank(message = "Rejection reason is mandatory")
    private String rejectionReason;
}
