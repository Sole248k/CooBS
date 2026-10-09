package com.group2.fse.transaction_service.interbank.outbound;

import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.client.dto.AccountLookupResponse;
import com.group2.fse.transaction_service.entity.InterbankMessage;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.client.AccountLookupUnavailableException;
import com.group2.fse.transaction_service.exception.TransactionNotFoundException;
import com.group2.fse.transaction_service.exception.TransferRejectedException;
import com.group2.fse.transaction_service.interbank.security.InterbankSecurityProperties;
import com.group2.fse.transaction_service.interbank.security.InterbankSecurityProperties.PartnerConfig;
import com.group2.fse.transaction_service.repository.InterbankMessageRepository;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * I4, the inter-bank gateway. The saga asks it to send one of our transfers to a partner bank
 * and acts on the answer; this is the only place that talks to another bank.
 *
 * Two rules decide every answer it gives:
 *
 * <ul>
 *   <li><b>One message per transaction.</b> The message ID is the transaction ID. A transfer the
 *       partner has answered is answered from the record; one that may have been sent is asked
 *       about before anything is sent again.</li>
 *   <li><b>An error means nothing was sent.</b> The saga releases the sender's money on a 4xx.
 *       So once a message may have left, this never answers with an error: whatever goes wrong
 *       after that point is PENDING, and the money stays reserved until the partner's answer
 *       is known.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundInterbankGateway {

    private static final String PROCESSING = "PROCESSING";
    private static final int MAX_REASON_CODE = 40;

    private final TxnTransactionRepository transactions;
    private final InterbankMessageRepository messages;
    private final InterbankSecurityProperties properties;
    private final OrchestrationClient accounts;
    private final InterbankClient interbankClient;

    public OutboundInterbankResponse send(OutboundInterbankRequest request) {
        TxnTransaction txn = transactions.findById(request.getTransactionId())
                .orElseThrow(() -> new TransactionNotFoundException(
                        "No transaction " + request.getTransactionId() + " to send to another bank."));
        PartnerConfig partner = partnerOf(txn);
        checkItIsTheSameTransfer(request, txn);

        InterbankMessage message = messages.findById(txn.getTransactionId()).orElseGet(() -> newMessage(txn, partner));
        if (message.decided()) {
            log.info("Transaction {} was already {} by {}; answering from the record",
                    txn.getTransactionId(), message.getStatus(), partner.getBankCode());
            return response(txn, message.getStatus(), null, message.getReasonCode());
        }

        boolean mayHaveBeenSent = InterbankMessage.PENDING.equals(message.getStatus());
        if (!mayHaveBeenSent) {
            // Nothing has gone out, so anything wrong up to here is still a plain "not sent".
            if (!PROCESSING.equals(txn.getStatus())) {
                throw refused("The transfer is " + txn.getStatus() + " and is no longer being sent.");
            }
            if (txn.getAmount().stripTrailingZeros().scale() > 2) {
                throw refused("An amount for another bank has at most two decimals.");
            }
        }
        String senderAccountNumber = senderAccountNumber(txn, mayHaveBeenSent);

        if (!mayHaveBeenSent) {
            // Recorded before the partner is called: if this service stops in the middle, the next
            // call knows the message may be with the partner and asks instead of sending.
            message.setStatus(InterbankMessage.PENDING);
            messages.save(message);
        }

        PartnerAnswer answer;
        try {
            answer = interbankClient.deliver(new OutboundTransfer(txn.getTransactionId(), partner,
                    properties.getOwnBankCode(), senderAccountNumber, message.getCounterpartyAccount(),
                    message.getCounterpartyName(), txn.getAmount(), txn.getCurrency(), txn.getRemarks(),
                    txn.getReferenceNo(), txn.getCreatedAt() != null ? txn.getCreatedAt() : Instant.now()),
                    mayHaveBeenSent);
        } catch (RuntimeException e) {
            log.error("Sending transaction {} to {} failed without an answer: {}",
                    txn.getTransactionId(), partner.getBankCode(), e.toString());
            answer = PartnerAnswer.unknown();
        }
        if (!answer.decided()) {
            log.warn("{} has not answered about transaction {}; it stays PENDING", partner.getBankCode(),
                    txn.getTransactionId());
            return response(txn, OutboundInterbankResponse.PENDING, null, null);
        }

        boolean accepted = answer.outcome() == PartnerAnswer.Outcome.ACCEPTED;
        message.setStatus(accepted ? InterbankMessage.ACCEPTED : InterbankMessage.REJECTED);
        message.setReasonCode(shortened(answer.reasonCode()));
        messages.save(message);
        log.info("{} {} transaction {} (reference {}, reason {})", partner.getBankCode(),
                accepted ? "accepted" : "refused", txn.getTransactionId(), answer.reference(), answer.reasonCode());
        return response(txn, message.getStatus(), answer.reference(), message.getReasonCode());
    }

    /** The partner the transfer is for. A transfer inside our own bank, or to a bank we have no agreement with, is not sent. */
    private PartnerConfig partnerOf(TxnTransaction txn) {
        String bankCode = txn.getDestinationBankCode();
        if (bankCode == null || properties.getOwnBankCode().equalsIgnoreCase(bankCode.trim())) {
            throw refused("The transfer is not for another bank.");
        }
        PartnerConfig partner = properties.getPartner(bankCode.trim());
        if (partner == null) {
            throw TransferRejectedException.interbankNotAvailable();
        }
        return partner;
    }

    /** The saga names the bank and the amount it reserved. A difference means two records disagree: send nothing. */
    private static void checkItIsTheSameTransfer(OutboundInterbankRequest request, TxnTransaction txn) {
        boolean sameBank = request.getDestinationBankCode().trim().equalsIgnoreCase(txn.getDestinationBankCode().trim());
        boolean sameAmount = request.getAmount().compareTo(txn.getAmount()) == 0;
        if (!sameBank || !sameAmount) {
            throw new TransferRejectedException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETERS",
                    "Invalid Request Parameters",
                    "The bank or the amount does not match transaction " + txn.getTransactionId() + ".");
        }
    }

    /**
     * The proposal's message names the sender's account. Before anything is sent, an account
     * that cannot be read stops the call (503: nothing was sent, try again). Once the message
     * may be with the partner the call must not fail, so the number is left out and the message
     * is only asked about.
     */
    private String senderAccountNumber(TxnTransaction txn, boolean mayHaveBeenSent) {
        try {
            return accounts.findAccountById(txn.getSourceAccountId())
                    .map(AccountLookupResponse::getAccountNumber)
                    .orElseThrow(() -> TransferRejectedException.accountNotFound("Source"));
        } catch (AccountLookupUnavailableException | TransferRejectedException e) {
            if (!mayHaveBeenSent) {
                throw e instanceof TransferRejectedException refusal ? refusal : TransferRejectedException.accountUnavailable();
            }
            log.warn("The sender's account of transaction {} could not be read; the partner is only asked: {}",
                    txn.getTransactionId(), e.getMessage());
            return null;
        }
    }

    /** For a transfer accepted before T1 recorded its message. */
    private static InterbankMessage newMessage(TxnTransaction txn, PartnerConfig partner) {
        return InterbankMessage.builder()
                .messageId(txn.getTransactionId())
                .direction(InterbankMessage.OUTBOUND)
                .partnerBankCode(partner.getBankCode())
                .transactionId(txn.getTransactionId())
                .counterpartyAccount(txn.getDestinationAccountNumber())
                .status(InterbankMessage.CREATED)
                .createdAt(Instant.now())
                .build();
    }

    private static OutboundInterbankResponse response(TxnTransaction txn, String status, String reference, String reasonCode) {
        return OutboundInterbankResponse.builder()
                .transactionId(txn.getTransactionId())
                .partnerStatus(status)
                .partnerReference(reference)
                .reasonCode(reasonCode)
                .build();
    }

    private static TransferRejectedException refused(String why) {
        return new TransferRejectedException(HttpStatus.UNPROCESSABLE_ENTITY, "INTERBANK_REJECTED",
                "Inter-bank Transfer Rejected", why);
    }

    private static String shortened(String reasonCode) {
        return reasonCode == null || reasonCode.length() <= MAX_REASON_CODE
                ? reasonCode : reasonCode.substring(0, MAX_REASON_CODE);
    }
}
