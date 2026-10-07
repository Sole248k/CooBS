package com.group2.fse.ledger_service.service;

import com.group2.fse.ledger_service.dto.*;
import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Hold;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.exception.HoldNotFoundException;
import com.group2.fse.ledger_service.posting.OrderedAccountLocks;
import com.group2.fse.ledger_service.posting.PostingEngine;
import com.group2.fse.ledger_service.repository.BalanceRepository;
import com.group2.fse.ledger_service.repository.HoldRepository;
import com.group2.fse.ledger_service.service.impl.HoldServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HoldServiceImpl Unit Tests (FIN-303)")
class HoldServiceImplTest {

    @Mock
    private HoldRepository holdRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private PostingEngine postingEngine;

    @Mock
    private com.group2.fse.ledger_service.service.OutboxService outboxService;

    @InjectMocks
    private HoldServiceImpl holdService;

    private Account sourceAccount;
    private Account destinationAccount;
    private Balance sourceBalance;
    private Balance destinationBalance;

    @BeforeEach
    void setUp() {
        sourceAccount = Account.builder()
                .accountId(1L)
                .accountNumber("ACC_10000001")
                .status("ACTIVE")
                .currency("PHP")
                .build();

        destinationAccount = Account.builder()
                .accountId(2L)
                .accountNumber("ACC_10000002")
                .status("ACTIVE")
                .currency("PHP")
                .build();

        sourceBalance = Balance.builder()
                .balanceId(1L)
                .account(sourceAccount)
                .availableBalance(new BigDecimal("50000.0000"))
                .ledgerBalance(new BigDecimal("50000.0000"))
                .build();

        destinationBalance = Balance.builder()
                .balanceId(2L)
                .account(destinationAccount)
                .availableBalance(new BigDecimal("25000.0000"))
                .ledgerBalance(new BigDecimal("25000.0000"))
                .build();
    }

    @Test
    @DisplayName("placeHold successfully reserves funds and updates available balance only")
    void testPlaceHoldSuccess() {
        PlaceHoldRequestDto request = PlaceHoldRequestDto.builder()
                .referenceNo("TX-20261003-8F3A21C0")
                .accountId(1L)
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .actorId(1L)
                .build();

        when(holdRepository.findByReferenceNo("TX-20261003-8F3A21C0")).thenReturn(Optional.empty());
        when(postingEngine.lockAccount(1L, true)).thenReturn(sourceBalance);
        when(postingEngine.calculateDebit(new BigDecimal("50000.0000"), new BigDecimal("2500.00")))
                .thenReturn(new BigDecimal("47500.0000"));
        when(balanceRepository.save(any(Balance.class))).thenReturn(sourceBalance);

        Hold savedHold = Hold.builder()
                .holdId(9001L)
                .account(sourceAccount)
                .referenceNo("TX-20261003-8F3A21C0")
                .amount(new BigDecimal("2500.0000"))
                .status("RESERVED")
                .createdAt(LocalDateTime.now())
                .build();
        when(holdRepository.save(any(Hold.class))).thenReturn(savedHold);

        PlaceHoldResponseDto response = holdService.placeHold(request, "idemp-1");

        assertThat(response.getHoldId()).isEqualTo(9001L);
        assertThat(response.getStatus()).isEqualTo("RESERVED");
        assertThat(response.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("47500.00");
        assertThat(response.getLedgerBalance()).isEqualByComparingTo("50000.00");

        verify(postingEngine).validatePositiveAmount(new BigDecimal("2500.00"), "Hold");
        verify(postingEngine).lockAccount(1L, true);
        verify(postingEngine).validateSufficientFunds(eq(1L), any(BigDecimal.class), eq(new BigDecimal("2500.00")));
        verify(balanceRepository).save(sourceBalance);
        verify(holdRepository).save(any(Hold.class));
    }

    @Test
    @DisplayName("placeHold returns existing hold when referenceNo already exists (idempotency)")
    void testPlaceHoldIdempotency() {
        PlaceHoldRequestDto request = PlaceHoldRequestDto.builder()
                .referenceNo("TX-20261003-8F3A21C0")
                .accountId(1L)
                .amount(new BigDecimal("2500.00"))
                .build();

        Hold existingHold = Hold.builder()
                .holdId(9001L)
                .account(sourceAccount)
                .referenceNo("TX-20261003-8F3A21C0")
                .amount(new BigDecimal("2500.0000"))
                .status("RESERVED")
                .createdAt(LocalDateTime.now())
                .build();

        when(holdRepository.findByReferenceNo("TX-20261003-8F3A21C0")).thenReturn(Optional.of(existingHold));
        when(balanceRepository.findByAccountId(1L)).thenReturn(Optional.of(sourceBalance));

        PlaceHoldResponseDto response = holdService.placeHold(request, "idemp-repeat");

        assertThat(response.getHoldId()).isEqualTo(9001L);
        assertThat(response.getReferenceNo()).isEqualTo("TX-20261003-8F3A21C0");
        verify(postingEngine, never()).lockAccount(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("captureHold successfully posts transfer and debits ledger balance")
    void testCaptureHoldSuccess() {
        Hold hold = Hold.builder()
                .holdId(9001L)
                .account(sourceAccount)
                .referenceNo("TX-20261003-8F3A21C0")
                .amount(new BigDecimal("2500.0000"))
                .status("RESERVED")
                .createdAt(LocalDateTime.now())
                .build();

        CaptureHoldRequestDto request = CaptureHoldRequestDto.builder()
                .destinationAccountId(2L)
                .remarks("Capture transfer")
                .actorId(1L)
                .clientIp("127.0.0.1")
                .build();

        when(holdRepository.findByIdForUpdate(9001L)).thenReturn(Optional.of(hold));
        when(postingEngine.lockAccountsInOrder(1L, 2L))
                .thenReturn(new OrderedAccountLocks(sourceBalance, destinationBalance));

        when(postingEngine.calculateDebit(new BigDecimal("50000.0000"), new BigDecimal("2500.0000")))
                .thenReturn(new BigDecimal("47500.0000"));
        when(postingEngine.calculateCredit(new BigDecimal("25000.0000"), new BigDecimal("2500.0000")))
                .thenReturn(new BigDecimal("27500.0000"));

        Transaction debitTxn = Transaction.builder().transactionId(5001L).build();
        Transaction creditTxn = Transaction.builder().transactionId(5002L).build();

        when(postingEngine.recordTransaction(eq(sourceAccount), eq("TX-20261003-8F3A21C0-D"), eq("DEBIT"), any(), any(), any(), eq("COMPLETED"), any()))
                .thenReturn(debitTxn);
        when(postingEngine.recordTransaction(eq(destinationAccount), eq("TX-20261003-8F3A21C0-C"), eq("CREDIT"), any(), any(), any(), eq("COMPLETED"), any()))
                .thenReturn(creditTxn);

        CaptureHoldResponseDto response = holdService.captureHold(9001L, request, "idemp-cap");

        assertThat(response.getHoldId()).isEqualTo(9001L);
        assertThat(response.getStatus()).isEqualTo("POSTED");
        assertThat(response.getDebitTransactionId()).isEqualTo(5001L);
        assertThat(response.getCreditTransactionId()).isEqualTo(5002L);
        assertThat(response.getAuditHash()).isNotNull();

        verify(balanceRepository).save(sourceBalance);
        verify(balanceRepository).save(destinationBalance);
        verify(holdRepository).save(hold);
    }

    @Test
    @DisplayName("releaseHold successfully restores available balance and cancels hold")
    void testReleaseHoldSuccess() {
        Hold hold = Hold.builder()
                .holdId(9001L)
                .account(sourceAccount)
                .referenceNo("TX-20261003-8F3A21C0")
                .amount(new BigDecimal("2500.0000"))
                .status("RESERVED")
                .createdAt(LocalDateTime.now())
                .build();

        sourceBalance.setAvailableBalance(new BigDecimal("47500.0000"));

        when(holdRepository.findByIdForUpdate(9001L)).thenReturn(Optional.of(hold));
        when(postingEngine.lockAccount(1L, false)).thenReturn(sourceBalance);
        when(postingEngine.calculateCredit(new BigDecimal("47500.0000"), new BigDecimal("2500.0000")))
                .thenReturn(new BigDecimal("50000.0000"));

        ReleaseHoldResponseDto response = holdService.releaseHold(9001L, ReleaseHoldRequestDto.builder().reason("TIMEOUT").build(), "idemp-rel");

        assertThat(response.getHoldId()).isEqualTo(9001L);
        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("50000.00");

        verify(balanceRepository).save(sourceBalance);
        verify(holdRepository).save(hold);
    }

    @Test
    @DisplayName("getHold throws HoldNotFoundException when hold does not exist")
    void testGetHoldNotFound() {
        when(holdRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holdService.getHold(9999L))
                .isInstanceOf(HoldNotFoundException.class);
    }
}
