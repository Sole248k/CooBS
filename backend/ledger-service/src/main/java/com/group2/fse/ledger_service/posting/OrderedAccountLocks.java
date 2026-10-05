package com.group2.fse.ledger_service.posting;

import com.group2.fse.ledger_service.entity.Balance;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Encapsulates the pessimistic locks acquired on two distinct accounts
 * in strict deadlock-free numerical order (min account ID, then max account ID).
 */
@Getter
@AllArgsConstructor
public class OrderedAccountLocks {
    private final Balance sourceBalance;
    private final Balance destinationBalance;
}
