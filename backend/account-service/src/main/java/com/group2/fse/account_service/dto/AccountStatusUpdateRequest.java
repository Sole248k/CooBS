package com.group2.fse.account_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusUpdateRequest {

    // CLOSED is left out on purpose: an account is closed through a closure request, which
    // checks that it holds no money. Any other text would be saved as it is and then read by
    // the ledger as "not active".
    @NotBlank(message = "Account status is required (ACTIVE, FROZEN or DORMANT)")
    @Pattern(regexp = "\\s*(ACTIVE|FROZEN|DORMANT)\\s*", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Account status must be ACTIVE, FROZEN or DORMANT. An account is closed through a closure request.")
    private String status;
}
