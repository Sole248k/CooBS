package com.group2.fse.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBeneficiaryRequest {

    @NotBlank(message = "Nickname is required")
    @Size(max = 60, message = "Nickname must not exceed 60 characters")
    private String nickname;

    @NotBlank(message = "Account number is required")
    @Size(max = 34, message = "Account number must not exceed 34 characters")
    private String accountNumber;

    @Builder.Default
    private String bankCode = "COOBS";

    private String beneficiaryName;
}
