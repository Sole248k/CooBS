package com.group2.fse.ledger_service.posting;

import com.group2.fse.ledger_service.entity.Transaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Result data carrier for an atomic double-entry fund transfer posting.
 */
@Getter
@Builder
public class PostingTransferResult {
    private final String transferReference;
    private final Long sourceAccountId;
    private final Long destinationAccountId;
    private final String sourceAccountNumber;
    private final String destinationAccountNumber;
    private final Long sourceCustomerId;
    private final Long destinationCustomerId;
    private final BigDecimal amount;
    private final BigDecimal sourcePreviousBalance;
    private final BigDecimal sourceNewBalance;
    private final BigDecimal destinationPreviousBalance;
    private final BigDecimal destinationNewBalance;
    private final Transaction debitTransaction;
    private final Transaction creditTransaction;
}
