package com.group2.fse.customer_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerProfileResponse {

    private Long customerId;
    private String username;
    private String email;
    private String kycStatus;
    private LocalDateTime createdAt;
    private KycDto kyc;
    private String membershipNumber;
    private String displayName;
    private String kycTier;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KycDto {
        private Long kycId;
        private String firstName;
        private String middleInitial;
        private String lastName;
        private String address;
        private String mobileNumber;
        private String civilStatus;
        private String occupation;
        private String status;
        private LocalDateTime createdAt;
    }
}
