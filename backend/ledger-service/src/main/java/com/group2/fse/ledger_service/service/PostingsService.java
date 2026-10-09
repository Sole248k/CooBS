package com.group2.fse.ledger_service.service;

import com.group2.fse.ledger_service.dto.PostingsRequestDto;
import com.group2.fse.ledger_service.dto.PostingsResponseDto;

/**
 * L8: balanced postings for the end-of-day batch and for inbound inter-bank credits.
 */
public interface PostingsService {

    /**
     * Posts each item as its own debit and credit, in its own transaction, and reports each
     * outcome. A posting that is already in the journal is reported POSTED and not posted again.
     *
     * @throws RuntimeException when the ledger itself fails (database, audit store). Nothing is
     *         reported as refused in that case; the caller repeats the request.
     */
    PostingsResponseDto post(PostingsRequestDto request, String clientIp);
}
