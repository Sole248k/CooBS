package com.group2.fse.transaction_service.interbank.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * I4 response. Aligned with contracts/examples/I4-interbank-outbound.response.json.
 * partnerStatus is ACCEPTED, REJECTED or PENDING; PENDING means the outcome is not known.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundInterbankResponse {

    public static final String PENDING = "PENDING";

    private UUID transactionId;
    private String partnerStatus;
    private String partnerReference;
    private String reasonCode;
}
