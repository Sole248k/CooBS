package com.group2.fse.transaction_service.interbank.service;

import com.group2.fse.transaction_service.interbank.dto.InterbankAccountLookupResponse;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferRequest;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferResponse;

public interface InterbankService {

    /**
     * Processes inbound transfer request from a partner bank (POST /api/v1/interbank/transfers).
     */
    InterbankTransferResponse processInboundTransfer(InterbankTransferRequest request);

    /**
     * Queries status of an inbound transfer by messageId (GET /api/v1/interbank/transfers/{messageId}).
     */
    InterbankTransferResponse getTransferStatus(String messageId);

    /**
     * Account confirmation & name check (GET /api/v1/interbank/accounts/{accountNumber}).
     */
    InterbankAccountLookupResponse checkAccount(String accountNumber);
}
