package com.group2.fse.transaction_service.interbank.outbound;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Delivers one transfer to a partner bank and comes back with what the partner said, following
 * the sending rules of the inter-bank proposal:
 *
 * <ul>
 *   <li>send, with our transaction ID as the message ID;</li>
 *   <li>no answer: ask the partner's status endpoint, up to three times, a little apart;</li>
 *   <li>status says it was never received: it is safe to send the same message once more;</li>
 *   <li>still nothing: the outcome is unknown, and stays unknown until a later call;</li>
 *   <li>the partner cannot be reached at all for a message it does not have: nothing was sent,
 *       which is an answer (UNREACHABLE), not an unknown.</li>
 * </ul>
 *
 * A partner with no address configured is our own loopback.
 */
@Slf4j
@Component
public class InterbankClient {

    private final ProposalPartnerAdapter proposalAdapter;
    private final LoopbackPartnerAdapter loopbackAdapter;
    private final AuraBankPartnerAdapter auraBankAdapter;
    private final int statusAttempts;
    private final long statusPauseMs;

    @Autowired
    public InterbankClient(ProposalPartnerAdapter proposalAdapter, LoopbackPartnerAdapter loopbackAdapter,
            @Nullable AuraBankPartnerAdapter auraBankAdapter,
            @Value("${app.interbank.outbound.status-attempts:3}") int statusAttempts,
            @Value("${app.interbank.outbound.status-pause-ms:1000}") long statusPauseMs) {
        this.proposalAdapter = proposalAdapter;
        this.loopbackAdapter = loopbackAdapter;
        this.auraBankAdapter = auraBankAdapter;
        this.statusAttempts = statusAttempts;
        this.statusPauseMs = statusPauseMs;
    }

    public InterbankClient(ProposalPartnerAdapter proposalAdapter, LoopbackPartnerAdapter loopbackAdapter,
            @Value("${app.interbank.outbound.status-attempts:3}") int statusAttempts,
            @Value("${app.interbank.outbound.status-pause-ms:1000}") long statusPauseMs) {
        this(proposalAdapter, loopbackAdapter, (AuraBankPartnerAdapter) null, statusAttempts, statusPauseMs);
    }

    /**
     * @param mayHaveBeenSent true when an earlier call handed this message to the partner, or may
     *                        have. The partner is then asked first, and the message is sent again
     *                        only if the partner says it never arrived.
     */
    public PartnerAnswer deliver(OutboundTransfer transfer, boolean mayHaveBeenSent) {
        PartnerAdapter partner = adapterFor(transfer);
        boolean resendAllowed = true;

        if (mayHaveBeenSent) {
            PartnerAnswer earlier = partner.status(transfer);
            if (earlier.outcome() != PartnerAnswer.Outcome.NOT_FOUND) {
                // Not reaching the partner now says nothing about a message it may already have.
                return earlier.decided() ? earlier : PartnerAnswer.unknown();
            }
            // The partner never received it: this is the one repeat the proposal allows.
            resendAllowed = false;
        }
        if (transfer.senderAccountNumber() == null) {
            // It can be asked about, but not sent: our own account lookup gave no answer.
            return PartnerAnswer.unknown();
        }

        PartnerAnswer answer = partner.send(transfer);
        if (answer.outcome() == PartnerAnswer.Outcome.UNREACHABLE && !mayHaveBeenSent) {
            // This is the first time the message is sent, and the send never connected. So it
            // was never sent, and the caller may give the sender's money back. A message that an
            // earlier call may have sent stays unknown instead: that request could still arrive.
            return answer;
        }
        for (int attempt = 1; !answer.decided() && attempt <= statusAttempts; attempt++) {
            if (!pause()) {
                return PartnerAnswer.unknown();
            }
            answer = partner.status(transfer);
            if (answer.outcome() == PartnerAnswer.Outcome.NOT_FOUND && resendAllowed) {
                log.info("{} never received message {}; sending it once more",
                        transfer.partner().getBankCode(), transfer.messageId());
                resendAllowed = false;
                answer = partner.send(transfer);
            }
        }
        return answer.decided() ? answer : PartnerAnswer.unknown();
    }

    private PartnerAdapter adapterFor(OutboundTransfer transfer) {
        String baseUrl = transfer.partner().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return loopbackAdapter;
        }
        if ("AURABANK".equalsIgnoreCase(transfer.partner().getBankCode()) && auraBankAdapter != null) {
            return auraBankAdapter;
        }
        return proposalAdapter;
    }

    private boolean pause() {
        try {
            Thread.sleep(statusPauseMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
