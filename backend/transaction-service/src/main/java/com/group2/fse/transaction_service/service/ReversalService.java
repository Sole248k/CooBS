package com.group2.fse.transaction_service.service;

import com.group2.fse.transaction_service.dto.TransferInitiateResponseDto;

public interface ReversalService {

    /**
     * T5. Accepts a request by staff to reverse a transfer in full and starts the reversal saga.
     *
     * @param transactionIdentifier the original transfer's transaction ID or reference number
     * @param reason why it is being reversed
     * @param idempotencyKey the caller's key; a repeat returns the first answer
     * @return the T1 response shape for the new reversal transaction, with reversesTransactionId
     */
    TransferInitiateResponseDto requestReversal(String transactionIdentifier, String reason, String idempotencyKey);
}
