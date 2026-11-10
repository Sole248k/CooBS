package com.group2.fse.ledger_service.service.impl;

import com.group2.fse.ledger_service.dto.PostingsRequestDto;
import com.group2.fse.ledger_service.dto.PostingsRequestDto.PostingItem;
import com.group2.fse.ledger_service.dto.PostingsResponseDto;
import com.group2.fse.ledger_service.dto.PostingsResponseDto.PostingResult;
import com.group2.fse.ledger_service.dto.TransferRequestDto;
import com.group2.fse.ledger_service.exception.AccountNotActiveException;
import com.group2.fse.ledger_service.exception.AccountNotFoundException;
import com.group2.fse.ledger_service.exception.InsufficientFundsException;
import com.group2.fse.ledger_service.exception.InvalidTransactionException;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.repository.TransactionRepository.Leg;
import com.group2.fse.ledger_service.service.AccountBalanceService;
import com.group2.fse.ledger_service.service.PostingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostingsServiceImpl implements PostingsService {

    static final String DUPLICATE_REFERENCE = "DUPLICATE_IDEMPOTENCY_KEY";

    private enum Journal { ABSENT, SAME_POSTING, DIFFERENT_POSTING }

    private final AccountBalanceService accountBalanceService;
    private final TransactionRepository transactionRepository;

    /**
     * Not transactional on purpose: each posting commits by itself in executeTransfer. A
     * request repeated after a failure finds the postings that already committed in the
     * journal and carries on with the rest.
     */
    @Override
    public PostingsResponseDto post(PostingsRequestDto request, String clientIp) {
        List<PostingResult> results = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int posted = 0;
        int failed = 0;

        for (PostingItem item : request.getPostings()) {
            String errorCode = postOne(item, request.getActorId(), clientIp);
            if (errorCode == null) {
                posted++;
                totalAmount = totalAmount.add(item.getAmount());
                results.add(new PostingResult(item.getReferenceNo(), PostingsResponseDto.POSTED, null));
            } else {
                failed++;
                results.add(new PostingResult(item.getReferenceNo(), PostingsResponseDto.FAILED, errorCode));
            }
        }

        return PostingsResponseDto.builder()
                .batchRunId(request.getBatchRunId())
                .stepName(request.getStepName())
                .transactionId(request.getTransactionId())
                .posted(posted)
                .failed(failed)
                .totalAmount(totalAmount.scale() < 2 ? totalAmount.setScale(2) : totalAmount)
                .results(results)
                .build();
    }

    /**
     * Returns null when the posting is in the journal, otherwise the code of the refusal.
     * Only a refusal is turned into a code. Anything else (the database, the audit store) is
     * thrown, so that the caller sees no answer rather than a false "refused".
     */
    private String postOne(PostingItem item, Long actorId, String clientIp) {
        if (Objects.equals(item.getDebitAccountId(), item.getCreditAccountId())) {
            return "INVALID_TRANSACTION";
        }

        Journal journal = journalOf(item);
        if (journal != Journal.ABSENT) {
            return replayed(item, journal);
        }

        try {
            accountBalanceService.executeTransfer(TransferRequestDto.builder()
                    .sourceAccountId(item.getDebitAccountId())
                    .destinationAccountId(item.getCreditAccountId())
                    .amount(item.getAmount())
                    .referenceNo(item.getReferenceNo())
                    .remarks(item.getRemarks())
                    .build(), actorId, clientIp);
            return null;
        } catch (InsufficientFundsException e) {
            return refused(item, "INSUFFICIENT_FUNDS", e);
        } catch (AccountNotFoundException e) {
            return refused(item, "ACCOUNT_NOT_FOUND", e);
        } catch (AccountNotActiveException e) {
            return refused(item, "ACCOUNT_NOT_ACTIVE", e);
        } catch (InvalidTransactionException | IllegalArgumentException e) {
            return refused(item, "INVALID_TRANSACTION", e);
        } catch (DataIntegrityViolationException e) {
            // The journal reference is unique. A request with the same reference that committed
            // between the check above and this insert ends here, and the journal now has the answer.
            Journal raced = journalOf(item);
            if (raced == Journal.ABSENT) {
                throw e;
            }
            return replayed(item, raced);
        }
    }

    /** Looks for the two legs of this reference and compares them with what is asked for now. */
    private Journal journalOf(PostingItem item) {
        String debitReference = item.getReferenceNo() + "-D";
        String creditReference = item.getReferenceNo() + "-C";
        List<Leg> legs = transactionRepository.findLegsByReferenceNoIn(List.of(debitReference, creditReference));
        if (legs.isEmpty()) {
            return Journal.ABSENT;
        }
        boolean debitMatches = legs.stream().anyMatch(leg -> matches(leg, debitReference, item.getDebitAccountId(), item));
        boolean creditMatches = legs.stream().anyMatch(leg -> matches(leg, creditReference, item.getCreditAccountId(), item));
        return debitMatches && creditMatches ? Journal.SAME_POSTING : Journal.DIFFERENT_POSTING;
    }

    private static boolean matches(Leg leg, String reference, Long accountId, PostingItem item) {
        return reference.equals(leg.getReferenceNo())
                && Objects.equals(accountId, leg.getAccountId())
                && leg.getAmount() != null && leg.getAmount().compareTo(item.getAmount()) == 0;
    }

    private String replayed(PostingItem item, Journal journal) {
        if (journal == Journal.SAME_POSTING) {
            log.info("L8 posting ref={} is already in the journal; reported POSTED and not posted again", item.getReferenceNo());
            return null;
        }
        log.warn("L8 posting ref={} was already used for different accounts or a different amount", item.getReferenceNo());
        return DUPLICATE_REFERENCE;
    }

    private String refused(PostingItem item, String errorCode, RuntimeException cause) {
        log.warn("L8 posting ref={} refused with {}: {}", item.getReferenceNo(), errorCode, cause.getMessage());
        return errorCode;
    }
}
