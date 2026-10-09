package com.group2.fse.transaction_service.interbank.service;

import com.group2.fse.transaction_service.client.AccountLookupUnavailableException;
import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.client.dto.AccountLookupResponse;
import com.group2.fse.transaction_service.client.dto.InboundInterbankSagaRequest;
import com.group2.fse.transaction_service.client.dto.InboundInterbankSagaResponse;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.interbank.dto.InterbankAccountLookupResponse;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferRequest;
import com.group2.fse.transaction_service.interbank.dto.InterbankTransferResponse;
import com.group2.fse.transaction_service.interbank.security.InterbankSecurityProperties;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.group2.fse.transaction_service.exception.TransferRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterbankServiceImpl implements InterbankService {

    private static final String OWN_BANK_CODE = "COOBS";
    private static final String CURRENCY_PHP = "PHP";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final TxnTransactionRepository transactionRepository;
    private final OrchestrationClient orchestrationClient;
    private final InterbankSecurityProperties properties;

    /**
     * Not one database transaction on purpose. The row is committed before the saga is called,
     * so that a second copy of the same message finds it (or is stopped by the unique key)
     * instead of posting under another reference, and so that a call that got no answer leaves
     * a PROCESSING row the partner's repeat picks up with the same transaction ID and reference.
     */
    @Override
    public InterbankTransferResponse processInboundTransfer(InterbankTransferRequest request) {
        log.info("Processing inbound interbank transfer: messageId={}, sourceBank={}, amount={}, destAccount={}",
                request.getMessageId(), request.getSourceBankCode(), request.getAmount(),
                request.getBeneficiary().getAccountNumber());

        // 1. Destination Bank Validation
        if (!OWN_BANK_CODE.equalsIgnoreCase(request.getDestinationBankCode())) {
            log.warn("Inbound transfer rejected: wrong destination bank {}", request.getDestinationBankCode());
            return InterbankTransferResponse.rejected(
                    request.getMessageId(),
                    "WRONG_DESTINATION_BANK",
                    "destinationBankCode must be " + OWN_BANK_CODE,
                    Instant.now().toString());
        }

        // 2. Currency Validation
        if (!CURRENCY_PHP.equalsIgnoreCase(request.getCurrency())) {
            log.warn("Inbound transfer rejected: unsupported currency {}", request.getCurrency());
            return InterbankTransferResponse.rejected(
                    request.getMessageId(),
                    "CURRENCY_NOT_SUPPORTED",
                    "Currency " + request.getCurrency() + " is not supported. Only PHP is accepted.",
                    Instant.now().toString());
        }

        // 3. Amount Validation
        BigDecimal amount;
        try {
            amount = new BigDecimal(request.getAmount());
            if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.scale() > 2) {
                return InterbankTransferResponse.rejected(
                        request.getMessageId(),
                        "INVALID_AMOUNT",
                        "Amount must be greater than zero with at most two decimal places.",
                        Instant.now().toString());
            }
        } catch (NumberFormatException ex) {
            return InterbankTransferResponse.rejected(
                    request.getMessageId(),
                    "INVALID_AMOUNT",
                    "Malformed amount string.",
                    Instant.now().toString());
        }

        // 4. Idempotency Check on messageId
        String idempotencyKey = "IB:" + request.getMessageId();
        Optional<TxnTransaction> existingOpt = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOpt.isPresent()) {
            return answerFromRow(request, existingOpt.get());
        }

        // 5. Beneficiary Account Lookup
        AccountLookupResponse beneficiaryAccount;
        try {
            Optional<AccountLookupResponse> accountOpt = orchestrationClient.findAccountByNumber(
                    request.getBeneficiary().getAccountNumber());
            if (accountOpt.isEmpty()) {
                log.warn("Inbound transfer rejected: beneficiary account {} not found",
                        request.getBeneficiary().getAccountNumber());
                return InterbankTransferResponse.rejected(
                        request.getMessageId(),
                        "ACCOUNT_NOT_FOUND",
                        "No account with that number.",
                        Instant.now().toString());
            }
            beneficiaryAccount = accountOpt.get();
        } catch (AccountLookupUnavailableException ex) {
            log.error("Beneficiary account lookup failed: {}", ex.getMessage());
            return InterbankTransferResponse.rejected(
                    request.getMessageId(),
                    "RECEIVER_ERROR",
                    "Account lookup service unavailable.",
                    Instant.now().toString());
        }

        // 6. Beneficiary Account Status Validation
        if (!"ACTIVE".equalsIgnoreCase(beneficiaryAccount.getStatus())) {
            log.warn("Inbound transfer rejected: beneficiary account {} status is {}",
                    request.getBeneficiary().getAccountNumber(), beneficiaryAccount.getStatus());
            return InterbankTransferResponse.rejected(
                    request.getMessageId(),
                    "ACCOUNT_NOT_ACTIVE",
                    "Account is inactive, frozen, or closed.",
                    Instant.now().toString());
        }

        // 7. Resolve Partner Settlement Account
        InterbankSecurityProperties.PartnerConfig partner = properties.getPartner(request.getSourceBankCode());
        Long settlementAccountId = partner != null && partner.getSettlementAccountId() != null
                ? partner.getSettlementAccountId()
                : properties.getDefaultSettlementAccountId();

        // 8. Generate Receiver Reference & Transaction ID
        UUID transactionId = UUID.randomUUID();
        String receiverRef = generateReceiverReference();
        String beneficiaryName = request.getBeneficiary().getName() != null && !request.getBeneficiary().getName().isBlank()
                ? maskName(request.getBeneficiary().getName())
                : maskName("CooBS Member");

        // 9. Persist TxnTransaction entity
        TxnTransaction txn = TxnTransaction.builder()
                .transactionId(transactionId)
                .referenceNo(receiverRef)
                .idempotencyKey(idempotencyKey)
                .channel("INTERBANK")
                .customerId(beneficiaryAccount.getCustomerId())
                .sourceAccountId(settlementAccountId)
                .destinationAccountId(beneficiaryAccount.getAccountId())
                .destinationAccountNumber(beneficiaryAccount.getAccountNumber())
                .destinationBankCode(OWN_BANK_CODE)
                .amount(amount)
                .currency(CURRENCY_PHP)
                .category("TRANSFER")
                .remarks("Inter-bank credit from " + request.getSourceBankCode() + " (" + request.getSender().getName() + ")")
                .status("PROCESSING")
                .createdAt(Instant.now())
                .build();
        try {
            transactionRepository.saveAndFlush(txn);
        } catch (DataIntegrityViolationException raced) {
            // Another copy of this message committed its row first; answer from that one.
            return answerFromRow(request, transactionRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> raced));
        }

        return settle(request, txn, beneficiaryName);
    }

    /** A repeated message: the stored outcome, or another try at a credit that never got an answer. */
    private InterbankTransferResponse answerFromRow(InterbankTransferRequest request, TxnTransaction existing) {
        if ("PROCESSING".equalsIgnoreCase(existing.getStatus())) {
            log.info("messageId={} was received before with no outcome; asking the saga again", request.getMessageId());
            return settle(request, existing, maskName(request.getBeneficiary().getName()));
        }
        log.info("Replaying response for duplicate messageId={}: status={}", request.getMessageId(), existing.getStatus());
        return replayResponse(request.getMessageId(), existing, request.getBeneficiary().getName());
    }

    /**
     * O4, synchronous. The saga and the ledger recognise the transaction ID and the reference,
     * so calling again for the same row is one posting. Only an answer from the saga ends the
     * row: no answer leaves it PROCESSING and tells the partner to send the message again.
     */
    private InterbankTransferResponse settle(InterbankTransferRequest request, TxnTransaction txn, String beneficiaryName) {
        InboundInterbankSagaResponse sagaResponse = orchestrationClient.triggerInboundInterbankSaga(
                InboundInterbankSagaRequest.builder()
                        .transactionId(txn.getTransactionId())
                        .referenceNo(txn.getReferenceNo())
                        .messageId(request.getMessageId())
                        .sourceBankCode(request.getSourceBankCode())
                        .settlementAccountId(txn.getSourceAccountId())
                        .destinationAccountId(txn.getDestinationAccountId())
                        .amount(txn.getAmount())
                        .currency(CURRENCY_PHP)
                        .build(),
                request.getMessageId());
        String sagaState = sagaResponse == null ? null : sagaResponse.getSagaState();

        Instant now = Instant.now();
        if ("COMPLETED".equalsIgnoreCase(sagaState)) {
            txn.setStatus("COMPLETED");
            txn.setCompletedAt(now);
            transactionRepository.save(txn);
            log.info("Inbound transfer {} credited", txn.getReferenceNo());
            return InterbankTransferResponse.accepted(request.getMessageId(), txn.getReferenceNo(), beneficiaryName, now.toString());
        }
        if ("FAILED".equalsIgnoreCase(sagaState)) {
            txn.setStatus("FAILED");
            txn.setFailureCode("RECEIVER_ERROR");
            txn.setCompletedAt(now);
            transactionRepository.save(txn);
            log.warn("Inbound transfer {} was refused by the ledger", txn.getReferenceNo());
            return InterbankTransferResponse.rejected(
                    request.getMessageId(),
                    "RECEIVER_ERROR",
                    "Ledger credit could not be completed.",
                    now.toString());
        }
        log.error("Inbound transfer {} got no answer from the saga (state {}); it stays PROCESSING",
                txn.getReferenceNo(), sagaState);
        throw outcomeUnknown();
    }

    /** 503: the credit may or may not have posted. The partner sends the same message again. */
    private static TransferRejectedException outcomeUnknown() {
        return new TransferRejectedException(HttpStatus.SERVICE_UNAVAILABLE, "RECEIVER_ERROR", "Outcome Not Known Yet",
                "The transfer has not been confirmed yet. Send the same message again.");
    }

    @Override
    @Transactional(readOnly = true)
    public InterbankTransferResponse getTransferStatus(String messageId) {
        String idempotencyKey = "IB:" + messageId;
        Optional<TxnTransaction> txnOpt = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (txnOpt.isEmpty()) {
            return InterbankTransferResponse.notFound(messageId);
        }

        TxnTransaction txn = txnOpt.get();
        return replayResponse(messageId, txn, null);
    }

    @Override
    @Transactional(readOnly = true)
    public InterbankAccountLookupResponse checkAccount(String accountNumber) {
        try {
            Optional<AccountLookupResponse> accountOpt = orchestrationClient.findAccountByNumber(accountNumber);
            if (accountOpt.isEmpty()) {
                return InterbankAccountLookupResponse.notFound(accountNumber);
            }
            AccountLookupResponse acc = accountOpt.get();
            boolean active = "ACTIVE".equalsIgnoreCase(acc.getStatus());
            return InterbankAccountLookupResponse.found(
                    accountNumber,
                    active,
                    maskName("CooBS Member"),
                    acc.getCurrency() != null ? acc.getCurrency() : CURRENCY_PHP
            );
        } catch (Exception ex) {
            log.error("Account verification failed for account {}: {}", accountNumber, ex.getMessage());
            return InterbankAccountLookupResponse.notFound(accountNumber);
        }
    }

    private InterbankTransferResponse replayResponse(String messageId, TxnTransaction txn, String fallbackName) {
        String name = fallbackName != null ? maskName(fallbackName) : maskName("CooBS Member");
        if ("COMPLETED".equalsIgnoreCase(txn.getStatus()) || "POSTED".equalsIgnoreCase(txn.getStatus())) {
            String time = txn.getCompletedAt() != null ? txn.getCompletedAt().toString() : txn.getCreatedAt().toString();
            return InterbankTransferResponse.accepted(messageId, txn.getReferenceNo(), name, time);
        } else if ("FAILED".equalsIgnoreCase(txn.getStatus())) {
            String time = txn.getCompletedAt() != null ? txn.getCompletedAt().toString() : txn.getCreatedAt().toString();
            String code = txn.getFailureCode() != null ? txn.getFailureCode() : "RECEIVER_ERROR";
            return InterbankTransferResponse.rejected(messageId, code, "Transfer failed.", time);
        } else {
            // Still PROCESSING: saying ACCEPTED here would confirm a credit that may not have posted.
            throw outcomeUnknown();
        }
    }

    private String generateReceiverReference() {
        String datePart = DATE_FORMATTER.format(Instant.now());
        int randomSeq = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "IB-" + datePart + "-" + randomSeq;
    }

    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "M***";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append("***");
                if (i < parts.length - 1) {
                    sb.append(" ");
                }
            }
        }
        return sb.toString();
    }
}
