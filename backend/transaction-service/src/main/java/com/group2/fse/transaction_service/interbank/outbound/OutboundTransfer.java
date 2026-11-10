package com.group2.fse.transaction_service.interbank.outbound;

import com.group2.fse.transaction_service.interbank.security.InterbankSecurityProperties.PartnerConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One of our transfers on its way to a partner bank, in our own terms. Each adapter turns it
 * into what its partner expects.
 *
 * @param messageId           our transaction ID; the partner's idempotency key
 * @param senderAccountNumber null when it could not be read; the message can then be asked
 *                            about but not sent
 */
public record OutboundTransfer(
        UUID messageId,
        PartnerConfig partner,
        String ownBankCode,
        String senderAccountNumber,
        String beneficiaryAccountNumber,
        String beneficiaryName,
        BigDecimal amount,
        String currency,
        String purpose,
        String senderReference,
        Instant createdAt) {
}
