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
public class BeneficiaryDto {
    private Long beneficiaryId;
    private Long customerId;
    private String nickname;
    private String accountNumber;
    private String bankCode;
    private Boolean active;
    private LocalDateTime createdAt;
}
