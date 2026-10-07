package com.group2.fse.transaction_service.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money leaves this service as a string with two to four decimals (contract section 2).
 * Response fields are written as strings with {@code @JsonFormat(shape = STRING)}, which prints
 * the value as it is, so the scale is fixed here first: 2500 and 2500.0000 both become
 * 2500.00, and 0.1250 becomes 0.125.
 */
public final class Money {

    private Money() {
    }

    public static BigDecimal toContractScale(BigDecimal value) {
        if (value == null) {
            return null;
        }
        int scale = Math.min(4, Math.max(2, value.stripTrailingZeros().scale()));
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
