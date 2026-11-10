package com.group2.fse.transaction_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response returned from orchestration-service (O4: POST /internal/v1/sagas/inbound-interbank).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundInterbankSagaResponse {

    private UUID transactionId;
    private String sagaState; // e.g. COMPLETED, FAILED
    private String referenceNo;
}
