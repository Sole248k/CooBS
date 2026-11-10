package com.group2.fse.ledger_service.reversal.service;

import com.group2.fse.ledger_service.reversal.dto.ReversalRequestDto;
import com.group2.fse.ledger_service.reversal.dto.ReversalResponseDto;

/**
 * Service contract for executing double-entry immutable ledger reversals (Handshake H3 L7).
 */
public interface ReversalService {

    /**
     * Executes an immutable double-entry reversal for an existing transfer or single-leg transaction.
     *
     * @param request reversal request containing originalReferenceNo, reason, and actor
     * @param actorId authenticated actor ID (if available from security context)
     * @param clientIp client IP address for forensic audit
     * @return ReversalResponseDto matching Handshake H3 L7 contract
     */
    ReversalResponseDto processReversal(ReversalRequestDto request, Long actorId, String clientIp);
}
