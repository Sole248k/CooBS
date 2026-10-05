package com.group2.fse.ledger_service.posting;

import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Transaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Result data carrier for a single posted transaction leg (debit, credit, hold, reversal).
 */
@Getter
@Builder
public class PostingLegResult {
    private final Long accountId;
    private final String accountNumber;
    private final Long customerId;
    private final Long transactionId;
    private final String referenceNo;
    private final String transactionType;
    private final BigDecimal amount;
    private final BigDecimal previousBalance;
    private final BigDecimal newBalance;
    private final Transaction transaction;
    private final Balance balance;
}
