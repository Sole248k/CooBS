package com.group2.fse.ledger_service.service;

import com.group2.fse.ledger_service.dto.CaptureHoldRequestDto;
import com.group2.fse.ledger_service.dto.PlaceHoldRequestDto;
import com.group2.fse.ledger_service.dto.PlaceHoldResponseDto;
import com.group2.fse.ledger_service.dto.ReleaseHoldRequestDto;
import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Hold;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.exception.DuplicateIdempotencyKeyException;
import com.group2.fse.ledger_service.posting.PostingEngineImpl;
import com.group2.fse.ledger_service.repository.BalanceRepository;
import com.group2.fse.ledger_service.repository.HoldRepository;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.service.impl.HoldServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hold lifecycle defects L5, L6, L7 and L11 from docs/backend-audit/04-ledger-review.md.
 * Real HoldServiceImpl and PostingEngineImpl over in-memory repositories.
 *
 * The HOLD row lock is simulated: findByIdForUpdate returns the committed row, which is what a
 * caller sees once it holds the lock, while findById hands every caller its own stale RESERVED
 * snapshot, which is what two unlocked readers see. Real threads against Oracle are not used here.
 */
@DisplayName("Hold locking and strict replay (L5, L6, L7, L11)")
class HoldLockingAndReplayTest {

    private final Map<Long, Balance> balances = new HashMap<>();
    private final Map<Long, Hold> holds = new HashMap<>();
    private final List<Transaction> journal = new ArrayList<>();
    private long seq = 1000;

    private BalanceRepository balanceRepository;
    private HoldRepository holdRepository;
    private HoldServiceImpl holdService;

    @BeforeEach
    void setUp() {
        balanceRepository = mock(BalanceRepository.class);
        holdRepository = mock(HoldRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);

        when(balanceRepository.findByAccountId(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(balances.get(i.<Long>getArgument(0))));
        when(balanceRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(balanceRepository.save(any(Balance.class))).thenAnswer(i -> {
            Balance b = i.getArgument(0);
            b.setAvailableBalance(b.getAvailableBalance().setScale(4, RoundingMode.HALF_UP));
            b.setLedgerBalance(b.getLedgerBalance().setScale(4, RoundingMode.HALF_UP));
            return b;
        });

        when(holdRepository.findByReferenceNo(anyString())).thenAnswer(i -> holds.values().stream()
                .filter(h -> h.getReferenceNo().equals(i.getArgument(0))).findFirst());
        when(holdRepository.findById(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(holds.get(i.<Long>getArgument(0))));
        when(holdRepository.findByIdForUpdate(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(holds.get(i.<Long>getArgument(0))));
        when(holdRepository.save(any(Hold.class))).thenAnswer(i -> {
            Hold h = i.getArgument(0);
            if (h.getHoldId() == null) {
                h.setHoldId(++seq);
            }
            holds.put(h.getHoldId(), h);
            return h;
        });

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setTransactionId(++seq);
            journal.add(t);
            return t;
        });

        PostingEngineImpl postingEngine = new PostingEngineImpl(balanceRepository, transactionRepository,
                mock(DualWriteLedgerAuditService.class));
        holdService = new HoldServiceImpl(holdRepository, balanceRepository, postingEngine, mock(OutboxService.class));

        open(1L, "1000.0000");
        open(2L, "500.0000");
    }

    private void open(Long id, String amount) {
        Account account = Account.builder().accountId(id).accountNumber("ACC" + id).status("ACTIVE").build();
        balances.put(id, Balance.builder().balanceId(id).account(account)
                .availableBalance(new BigDecimal(amount)).ledgerBalance(new BigDecimal(amount)).build());
    }

    private BigDecimal avail(Long id) {
        return balances.get(id).getAvailableBalance();
    }

    private Hold freshCopy(Hold h) {
        return Hold.builder().holdId(h.getHoldId()).account(h.getAccount()).referenceNo(h.getReferenceNo())
                .amount(h.getAmount()).status(h.getStatus()).createdAt(h.getCreatedAt()).build();
    }

    private PlaceHoldResponseDto placeHold(String ref, Long accountId, String amount) {
        return holdService.placeHold(PlaceHoldRequestDto.builder().referenceNo(ref).accountId(accountId)
                .amount(new BigDecimal(amount)).build(), null);
    }

    private Long placeHold(String ref, String amount) {
        return placeHold(ref, 1L, amount).getHoldId();
    }

    /** Every unlocked read of the hold returns its own snapshot of the state at this moment. */
    private void staleUnlockedReads(Long holdId) {
        Hold snapshot = freshCopy(holds.get(holdId));
        when(holdRepository.findById(holdId)).thenAnswer(i -> Optional.of(freshCopy(snapshot)));
    }

    private boolean succeeds(Runnable r) {
        try {
            r.run();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- L5, L6: a hold is resolved exactly once ---------------------------------------------

    @Test
    void captureAndReleaseOfSameHold_cannotBothSucceed() {
        Long holdId = placeHold("TX-20261006-BBBB0001", "100.00");
        staleUnlockedReads(holdId);

        boolean captured = succeeds(() -> holdService.captureHold(holdId,
                CaptureHoldRequestDto.builder().destinationAccountId(2L).build(), null));
        boolean released = succeeds(() -> holdService.releaseHold(holdId,
                ReleaseHoldRequestDto.builder().reason("TIMEOUT").build(), null));

        assertThat(captured && released).as("capture and release both succeeded").isFalse();
        assertThat(avail(1L).add(avail(2L))).as("money created").isEqualByComparingTo("1500.0000");
    }

    @Test
    void releaseAndCaptureOfSameHold_cannotBothSucceed() {
        Long holdId = placeHold("TX-20261006-BBBB0002", "100.00");
        staleUnlockedReads(holdId);

        boolean released = succeeds(() -> holdService.releaseHold(holdId,
                ReleaseHoldRequestDto.builder().reason("TIMEOUT").build(), null));
        boolean captured = succeeds(() -> holdService.captureHold(holdId,
                CaptureHoldRequestDto.builder().destinationAccountId(2L).build(), null));

        assertThat(released && captured).as("release and capture both succeeded").isFalse();
        assertThat(avail(1L).add(avail(2L))).as("money created").isEqualByComparingTo("1500.0000");
    }

    @Test
    void doubleReleaseOfSameHold_restoresFundsOnce() {
        Long holdId = placeHold("TX-20261006-CCCC0001", "100.00");
        staleUnlockedReads(holdId);

        holdService.releaseHold(holdId, ReleaseHoldRequestDto.builder().reason("A").build(), null);
        succeeds(() -> holdService.releaseHold(holdId, ReleaseHoldRequestDto.builder().reason("B").build(), null));

        assertThat(avail(1L)).isEqualByComparingTo("1000.0000");
    }

    @Test
    void captureHold_locksTheHoldRowBeforeAnyBalance() {
        Long holdId = placeHold("TX-20261006-CCCC0002", "100.00");
        InOrder order = inOrder(holdRepository, balanceRepository);

        holdService.captureHold(holdId, CaptureHoldRequestDto.builder().destinationAccountId(2L).build(), null);

        order.verify(holdRepository).findByIdForUpdate(holdId);
        order.verify(balanceRepository).findByAccountId(1L);
        order.verify(balanceRepository).findByAccountId(2L);
    }

    @Test
    void releaseHold_locksTheHoldRowBeforeTheBalance() {
        Long holdId = placeHold("TX-20261006-CCCC0003", "100.00");
        InOrder order = inOrder(holdRepository, balanceRepository);

        holdService.releaseHold(holdId, ReleaseHoldRequestDto.builder().reason("X").build(), null);

        order.verify(holdRepository).findByIdForUpdate(holdId);
        order.verify(balanceRepository).findByAccountId(1L);
    }

    // ---- L7: strict place-hold replay --------------------------------------------------------

    @Test
    void placeHoldReplayWithDifferentAmount_isRejectedNotSilentlyAccepted() {
        placeHold("TX-20261006-DDDD0001", "100.00");

        assertThatThrownBy(() -> placeHold("TX-20261006-DDDD0001", "900.00"))
                .as("same reference, different amount")
                .isInstanceOf(DuplicateIdempotencyKeyException.class);
        assertThat(avail(1L)).as("only the first hold is reserved").isEqualByComparingTo("900.0000");
    }

    @Test
    void placeHoldReplayWithDifferentAccount_isRejected() {
        placeHold("TX-20261006-DDDD0002", "100.00");

        assertThatThrownBy(() -> placeHold("TX-20261006-DDDD0002", 2L, "100.00"))
                .as("same reference, different account")
                .isInstanceOf(DuplicateIdempotencyKeyException.class);
        assertThat(avail(2L)).isEqualByComparingTo("500.0000");
    }

    @Test
    void placeHoldReplayWithSameAccountAndAmount_returnsTheExistingHold() {
        Long holdId = placeHold("TX-20261006-DDDD0003", "100.00");

        PlaceHoldResponseDto replay = placeHold("TX-20261006-DDDD0003", 1L, "100.0000");

        assertThat(replay.getHoldId()).isEqualTo(holdId);
        assertThat(avail(1L)).as("funds reserved once").isEqualByComparingTo("900.0000");
    }

    @Test
    void placeHoldRacingDuplicate_seesTheOtherHoldOnceItOwnsTheBalanceLock() {
        String ref = "TX-20261006-DDDD0004";
        Long winnerHoldId = placeHold(ref, "100.00");
        // The loser ran its first reference check before the winner committed and saw nothing.
        // It then waits on the balance lock; the check after the lock sees the committed hold.
        AtomicInteger lookups = new AtomicInteger();
        when(holdRepository.findByReferenceNo(ref)).thenAnswer(i ->
                lookups.getAndIncrement() == 0 ? Optional.empty() : Optional.of(holds.get(winnerHoldId)));

        PlaceHoldResponseDto loser = placeHold(ref, 1L, "100.00");

        assertThat(loser.getHoldId()).isEqualTo(winnerHoldId);
        assertThat(avail(1L)).as("funds reserved once").isEqualByComparingTo("900.0000");
        assertThat(holds).hasSize(1);
    }

    // ---- L11: journal references fit VARCHAR2(36) --------------------------------------------

    @Test
    void referenceLongerThan34Chars_canStillBeCaptured() {
        String ref = "TX-20261006-0123456789-0123456789-AB"; // 36 chars, accepted by HOLD.reference_no
        Long holdId = placeHold(ref, "100.00");

        holdService.captureHold(holdId, CaptureHoldRequestDto.builder().destinationAccountId(2L).build(), null);

        assertThat(journal).hasSize(2).allSatisfy(t -> assertThat(t.getReferenceNo().length())
                .as("TRANSACTION.reference_no is VARCHAR2(36)").isLessThanOrEqualTo(36));
    }

    @Test
    void referenceOf34Chars_keepsItsFullReferenceOnBothLegs() {
        String ref = "TX-20261006-0123456789-0123456789A"; // 34 chars, the longest the API accepts
        Long holdId = placeHold(ref, "100.00");

        holdService.captureHold(holdId, CaptureHoldRequestDto.builder().destinationAccountId(2L).build(), null);

        assertThat(journal).extracting(Transaction::getReferenceNo).containsExactly(ref + "-D", ref + "-C");
    }
}
