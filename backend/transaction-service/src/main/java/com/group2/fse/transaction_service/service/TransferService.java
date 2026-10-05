package com.group2.fse.transaction_service.service;

import com.group2.fse.transaction_service.dto.TransferInitiateResponseDto;
import com.group2.fse.transaction_service.dto.TransferRequestDto;

/**
 * Service handling transfer ingress and tracking generation.
 */
public interface TransferService {

    /**
     * Ingresses a transfer request and generates immediate tracking reference TX-XXXXXX.
     *
     * @param request the transfer details
     * @param idempotencyKey optional client idempotency key
     * @return the transfer tracking response
     */
    TransferInitiateResponseDto initiateTransfer(TransferRequestDto request, String idempotencyKey);
}
