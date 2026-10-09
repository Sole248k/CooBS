package com.group2.fse.transaction_service.interbank.outbound;

/**
 * How one kind of partner bank is spoken to. A partner whose payload differs from the proposal
 * gets its own adapter; nothing else changes (contract section 14).
 *
 * Neither method throws for a partner that does not answer: that is {@link PartnerAnswer#unknown()}.
 */
public interface PartnerAdapter {

    /** Sends the transfer. The partner treats the message ID as an idempotency key. */
    PartnerAnswer send(OutboundTransfer transfer);

    /** Asks the partner what became of a message that may have been sent. */
    PartnerAnswer status(OutboundTransfer transfer);
}
