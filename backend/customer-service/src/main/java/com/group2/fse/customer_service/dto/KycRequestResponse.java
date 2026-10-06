package com.group2.fse.customer_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KycRequestResponse {
    private Long kycRequestId;
    private Long kycId;
    private Long customerId;
    private String status;
    private Long approvedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private String message;

    private String newFirstName;
    private String newMiddleInitial;
    private String newLastName;
    private String newAddress;
    private String newMobileNumber;
    private String newCivilStatus;
    private String newOccupation;
}
