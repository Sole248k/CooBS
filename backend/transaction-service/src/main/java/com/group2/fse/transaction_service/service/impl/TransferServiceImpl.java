package com.group2.fse.transaction_service.service.impl;

import com.group2.fse.transaction_service.client.AccountLookupUnavailableException;
import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.client.dto.AccountLookupResponse;
import com.group2.fse.transaction_service.client.dto.StartTransferSagaRequest;
import com.group2.fse.transaction_service.dto.Money;
import com.group2.fse.transaction_service.dto.TransferInitiateResponseDto;
import com.group2.fse.transaction_service.dto.TransferRequestDto;
import com.group2.fse.transaction_service.entity.TxnStageEvent;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.event.StageEventPublisher;
import com.group2.fse.transaction_service.exception.DuplicateIdempotencyKeyException;
import com.group2.fse.transaction_service.exception.InvalidTransactionException;
import com.group2.fse.transaction_service.exception.TransferRejectedException;
import com.group2.fse.transaction_service.repository.TxnStageEventRepository;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import com.group2.fse.transaction_service.security.UserPrincipal;
import com.group2.fse.transaction_service.service.IdempotencyService;
import com.group2.fse.transaction_service.service.TransferService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Task: FIN-303, FIN-304
 * T1, the transfer ingress. It checks who is asking and that both accounts exist, saves the
 * transaction with its first three stages in one database transaction, and then starts the
 * saga. It answers 202 at once; the saga decides the outcome.
 *
 * Nothing here guesses. An account that cannot be confirmed stops the request, because a
 * guessed customer or destination would move real money for the wrong person.
 */
@Slf4j
@Service
public class TransferServiceImpl implements TransferService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);
    private static final String OWN_BANK_CODE = "COOBS";
    /** The stored key is "<callerId>:<key>" and txn_transaction.idempotency_key is VARCHAR(64). */
    private static final int MAX_KEY_LENGTH = 40;

    private final IdempotencyService idempotencyService;
    private final TxnTransactionRepository transactionRepository;
    private final TxnStageEventRepository stageEventRepository;
    private final OrchestrationClient orchestrationClient;
    private final StageEventPublisher stageEventPublisher;
    private final TransactionTemplate transactionTemplate;

    public TransferServiceImpl(IdempotencyService idempotencyService,
                               TxnTransactionRepository transactionRepository,
                               TxnStageEventRepository stageEventRepository,
                               OrchestrationClient orchestrationClient,
                               StageEventPublisher stageEventPublisher,
                               PlatformTransactionManager transactionManager) {
        this.idempotencyService = idempotencyService;
        this.transactionRepository = transactionRepository;
        this.stageEventRepository = stageEventRepository;
        this.orchestrationClient = orchestrationClient;
        this.stageEventPublisher = stageEventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public TransferInitiateResponseDto initiateTransfer(TransferRequestDto request, String idempotencyKey) {
        validateRequest(request);
        Caller caller = currentCaller();
        String key = scopedKey(caller, idempotencyKey);

        // A repeat of a request that was already accepted gets the first answer, whatever
        // has happened to the transfer since.
        Optional<TxnTransaction> accepted = transactionRepository.findByIdempotencyKey(key);
        if (accepted.isPresent()) {
            return replay(accepted.get(), request);
        }

        if (!OWN_BANK_CODE.equalsIgnoreCase(bankCodeOf(request))) {
            throw TransferRejectedException.interbankNotAvailable();
        }
        AccountLookupResponse source = findAccount(
                () -> orchestrationClient.findAccountById(request.getSourceAccountId()), "Source");
        if (!caller.staff() && !Objects.equals(source.getCustomerId(), caller.userId())) {
            log.warn("User {} tried to send from account {}, which belongs to customer {}",
                    caller.userId(), source.getAccountId(), source.getCustomerId());
            throw TransferRejectedException.accountNotOwned();
        }
        AccountLookupResponse destination = findAccount(
                () -> orchestrationClient.findAccountByNumber(request.getDestinationAccountNumber()), "Destination");

        if (!idempotencyService.tryAcquire(key, LOCK_TTL)) {
            log.warn("A request with the same Idempotency-Key is still being accepted; answering 409");
            throw new DuplicateIdempotencyKeyException(
                    "A transaction with this Idempotency-Key is currently being processed. Duplicate submission blocked.");
        }
        try {
            // The first request may have committed between the check above and the lock.
            accepted = transactionRepository.findByIdempotencyKey(key);
            if (accepted.isPresent()) {
                return replay(accepted.get(), request);
            }
            TxnTransaction txn = transactionTemplate.execute(status -> save(request, caller, key, source, destination));
            publishIngressStages(txn);
            startSaga(txn, caller);
            return firstResponse(txn);
        } catch (DataIntegrityViolationException raced) {
            // The unique index on the key caught a duplicate the lock did not.
            return transactionRepository.findByIdempotencyKey(key)
                    .map(existing -> replay(existing, request))
                    .orElseThrow(() -> raced);
        } finally {
            idempotencyService.release(key);
        }
    }

    private TxnTransaction save(TransferRequestDto request, Caller caller, String key,
                                AccountLookupResponse source, AccountLookupResponse destination) {
        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.now();
        String randomHex = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String referenceNo = "TX-" + DATE_FORMATTER.format(now) + "-" + randomHex;

        TxnTransaction txn = TxnTransaction.builder()
                .transactionId(transactionId)
                .referenceNo(referenceNo)
                .idempotencyKey(key)
                .channel(caller.staff() ? "TELLER" : "APP")
                .customerId(source.getCustomerId())
                .sourceAccountId(source.getAccountId())
                .destinationAccountId(destination.getAccountId())
                .destinationAccountNumber(request.getDestinationAccountNumber())
                .destinationBankCode(OWN_BANK_CODE)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "PHP")
                .category(request.getCategory() != null ? request.getCategory() : "TRANSFER")
                .remarks(request.getRemarks())
                .status("PROCESSING")
                .createdAt(now)
                .build();
        transactionRepository.save(txn);

        stageEventRepository.saveAll(List.of(
                stage(transactionId, 1, "INITIATED", now),
                stage(transactionId, 2, "VALIDATED", now.plusMillis(10)),
                stage(transactionId, 3, "AUTHENTICATED", now.plusMillis(20))));
        return txn;
    }

    private static TxnStageEvent stage(UUID transactionId, int stageNo, String stageName, Instant occurredAt) {
        return TxnStageEvent.builder()
                .transactionId(transactionId)
                .stageNo(stageNo)
                .stageName(stageName)
                .outcome("PASSED")
                .occurredAt(occurredAt)
                .build();
    }

    // Published after the commit, so that a consumer never reads a stage for a row that is not there yet.
    private void publishIngressStages(TxnTransaction txn) {
        UUID id = txn.getTransactionId();
        String ref = txn.getReferenceNo();
        stageEventPublisher.publishStage(id, ref, 1, "INITIATED", "PASSED", "Ingress Intake accepted");
        stageEventPublisher.publishStage(id, ref, 2, "VALIDATED", "PASSED", "Preflight validation passed");
        stageEventPublisher.publishStage(id, ref, 3, "AUTHENTICATED", "PASSED", "Dispatched to orchestrator");
    }

    /**
     * O1, sent in the background. If it does not arrive, SagaStartRedispatcher sends it again:
     * the row is saved and O1 is idempotent on the transaction ID.
     */
    private void startSaga(TxnTransaction txn, Caller caller) {
        try {
            orchestrationClient.triggerTransferSagaAsync(
                    sagaRequest(txn, caller.userId(), caller.staff() ? "STAFF" : "CUSTOMER"), correlationId());
        } catch (RuntimeException e) {
            log.error("Could not hand saga start for {} to the background executor; it will be re-dispatched: {}",
                    txn.getTransactionId(), e.getMessage());
        }
    }

    static StartTransferSagaRequest sagaRequest(TxnTransaction txn, Long actorId, String actorType) {
        return StartTransferSagaRequest.builder()
                .transactionId(txn.getTransactionId())
                .referenceNo(txn.getReferenceNo())
                .channel(txn.getChannel())
                .customerId(txn.getCustomerId())
                .sourceAccountId(txn.getSourceAccountId())
                .destinationAccountId(txn.getDestinationAccountId())
                .destinationBankCode(txn.getDestinationBankCode())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .actorId(actorId)
                .actorType(actorType)
                .build();
    }

    /** The answer the first request got. A different transfer under the same key is refused. */
    private TransferInitiateResponseDto replay(TxnTransaction accepted, TransferRequestDto request) {
        boolean sameTransfer = Objects.equals(accepted.getSourceAccountId(), request.getSourceAccountId())
                && Objects.equals(accepted.getDestinationAccountNumber(), request.getDestinationAccountNumber())
                && accepted.getAmount().compareTo(request.getAmount()) == 0;
        if (!sameTransfer) {
            throw TransferRejectedException.keyReused();
        }
        log.info("Idempotent replay of transfer {}", accepted.getTransactionId());
        TransferInitiateResponseDto response = firstResponse(accepted);
        response.setReplayed(true);
        return response;
    }

    private static TransferInitiateResponseDto firstResponse(TxnTransaction txn) {
        return TransferInitiateResponseDto.builder()
                .transactionId(txn.getTransactionId().toString())
                .referenceNo(txn.getReferenceNo())
                .status("INITIATED")
                .amount(Money.toContractScale(txn.getAmount()))
                .currency(txn.getCurrency())
                .createdAt(txn.getCreatedAt())
                .timelineUrl("/api/v1/transactions/" + txn.getTransactionId() + "/timeline")
                .build();
    }

    private AccountLookupResponse findAccount(java.util.function.Supplier<Optional<AccountLookupResponse>> lookup,
                                              String which) {
        try {
            return lookup.get().orElseThrow(() -> TransferRejectedException.accountNotFound(which));
        } catch (AccountLookupUnavailableException e) {
            log.error("{} account could not be checked: {}", which, e.getMessage());
            throw TransferRejectedException.accountUnavailable();
        }
    }

    private void validateRequest(TransferRequestDto request) {
        if (request == null) {
            throw new InvalidTransactionException("Transfer request must not be null.");
        }
        if (request.getSourceAccountId() == null || request.getSourceAccountId() <= 0) {
            throw new InvalidTransactionException("Source account ID must be positive.");
        }
        if (request.getDestinationAccountNumber() == null || request.getDestinationAccountNumber().isBlank()) {
            throw new InvalidTransactionException("Destination account number is required.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transfer amount must be greater than zero.");
        }
    }

    private static String bankCodeOf(TransferRequestDto request) {
        String code = request.getDestinationBankCode();
        return code == null || code.isBlank() ? OWN_BANK_CODE : code.trim();
    }

    /** Keys belong to the caller: the same key from two people is two different keys. */
    private static String scopedKey(Caller caller, String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            throw new TransferRejectedException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETERS",
                    "Invalid Request Parameters",
                    "Idempotency-Key is required and must be at most " + MAX_KEY_LENGTH + " characters.");
        }
        return caller.userId() + ":" + key;
    }

    private static Caller currentCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal) || principal.getUserId() == null) {
            throw new TransferRejectedException(HttpStatus.FORBIDDEN, "AUTH_ACCESS_DENIED", "Access Denied",
                    "A signed-in customer or staff member is required.");
        }
        boolean staff = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TELLER".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
        return new Caller(principal.getUserId(), staff);
    }

    /** The incoming correlation ID is carried on, so one request can be followed across services. */
    private static String correlationId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String header = attributes.getRequest().getHeader("X-Correlation-ID");
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        return UUID.randomUUID().toString();
    }

    private record Caller(Long userId, boolean staff) {
    }
}
