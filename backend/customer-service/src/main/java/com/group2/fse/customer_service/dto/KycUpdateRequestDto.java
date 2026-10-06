package com.group2.fse.customer_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycUpdateRequestDto {
    private String newFirstName;
    private String newMiddleInitial;
    private String newLastName;
    private String newAddress;
    private String newMobileNumber;
    private String newCivilStatus;
    private String newOccupation;
}
