package com.group2.fse.transaction_service.interbank.outbound;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The partner used when a partner bank has no address configured: our own loopback. It stands in
 * for the other bank so that the outbound path can be run and shown without one (the service
 * scope document: "against our own loopback; a real partner is optional").
 *
 * It accepts every transfer, with one exception that lets a refusal be shown: an account number
 * beginning with 0000 does not exist at this pretend bank. It answers the same way however often
 * it is asked, as a real partner does for a repeated message ID.
 */
@Slf4j
@Component
public class LoopbackPartnerAdapter implements PartnerAdapter {

    static final String UNKNOWN_ACCOUNT_PREFIX = "0000";

    @Override
    public PartnerAnswer send(OutboundTransfer transfer) {
        return answer(transfer);
    }

    @Override
    public PartnerAnswer status(OutboundTransfer transfer) {
        return answer(transfer);
    }

    private static PartnerAnswer answer(OutboundTransfer transfer) {
        String bank = transfer.partner().getBankCode();
        if (transfer.beneficiaryAccountNumber().startsWith(UNKNOWN_ACCOUNT_PREFIX)) {
            log.info("Loopback partner {} refused message {}: no such account", bank, transfer.messageId());
            return PartnerAnswer.rejected("ACCOUNT_NOT_FOUND");
        }
        String reference = "LB-" + transfer.messageId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        log.info("Loopback partner {} accepted message {} as {}", bank, transfer.messageId(), reference);
        return PartnerAnswer.accepted(reference);
    }
}
