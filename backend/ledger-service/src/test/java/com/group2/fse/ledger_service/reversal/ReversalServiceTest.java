package com.group2.fse.ledger_service.reversal;

import com.group2.fse.ledger_service.entity.Account;
import com.group2.fse.ledger_service.entity.Balance;
import com.group2.fse.ledger_service.entity.Customer;
import com.group2.fse.ledger_service.entity.Transaction;
import com.group2.fse.ledger_service.exception.InsufficientFundsException;
import com.group2.fse.ledger_service.exception.InvalidTransactionException;
import com.group2.fse.ledger_service.exception.TransactionAlreadyReversedException;
import com.group2.fse.ledger_service.exception.TransactionNotFoundException;
import com.group2.fse.ledger_service.posting.OrderedAccountLocks;
import com.group2.fse.ledger_service.posting.PostingEngine;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.reversal.dto.ReversalRequestDto;
import com.group2.fse.ledger_service.reversal.dto.ReversalResponseDto;
import com.group2.fse.ledger_service.reversal.service.impl.ReversalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Core Ledger Reversal Service Unit Tests")
class ReversalServiceTest {

    @Mock
    private PostingEngine postingEngine;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ReversalServiceImpl reversalService;

    private Account senderAccount;
    private Account recipientAccount;
    private Balance senderBalance;
    private Balance recipientBalance;
    private Transaction originalDebitTxn;
    private Transaction originalCreditTxn;

    @BeforeEach
    void setUp() {
        Customer senderCust = Customer.builder().customerId(101L).build();
        Customer recipientCust = Customer.builder().customerId(102L).build();

        senderAccount = Account.builder()
                .accountId(1L)
                .accountNumber("ACC-001")
                .currency("PHP")
                .status("ACTIVE")
                .customer(senderCust)
                .build();

        recipientAccount = Account.builder()
                .accountId(2L)
                .accountNumber("ACC-002")
                .currency("PHP")
                .status("ACTIVE")
                .customer(recipientCust)
                .build();

        senderBalance = Balance.builder()
                .balanceId(11L)
                .account(senderAccount)
                .availableBalance(new BigDecimal("47125.0000"))
                .build();

        recipientBalance = Balance.builder()
                .balanceId(12L)
                .account(recipientAccount)
                .availableBalance(new BigDecimal("27875.0000"))
                .build();

        originalDebitTxn = Transaction.builder()
                .transactionId(5001L)
                .account(senderAccount)
                .referenceNo("TX-20261003-8F3A21C0-D")
                .transactionType("DEBIT")
                .amount(new BigDecimal("2500.0000"))
                .previousBalance(new BigDecimal("49625.0000"))
                .newBalance(new BigDecimal("47125.0000"))
                .status("COMPLETED")
                .build();

        originalCreditTxn = Transaction.builder()
                .transactionId(5002L)
                .account(recipientAccount)
                .referenceNo("TX-20261003-8F3A21C0-C")
                .transactionType("CREDIT")
                .amount(new BigDecimal("2500.0000"))
                .previousBalance(new BigDecimal("25375.0000"))
                .newBalance(new BigDecimal("27875.0000"))
                .status("COMPLETED")
                .build();

        // No reversal is in the journal yet, whatever reversal reference a test uses.
        lenient().when(transactionRepository.findByOriginalReferenceNo(startsWith("RV-"))).thenReturn(List.of());
    }

    @Test
    @DisplayName("Successfully reverse a two-legged transfer with immutable counter-entries")
    void testProcessReversal_Transfer_Success() {
        ReversalRequestDto request = ReversalRequestDto.builder()
                .originalReferenceNo("TX-20261003-8F3A21C0")
                .reversalReferenceNo("RV-20261003-11AA22BB")
                .reason("Customer dispute approved")
                .actorId(3L)
                .build();

        when(transactionRepository.findByOriginalReferenceNo("TX-20261003-8F3A21C0"))
                .thenReturn(List.of(originalDebitTxn, originalCreditTxn));
        when(transactionRepository.existsByReversesTransaction_TransactionId(anyLong())).thenReturn(false);

        OrderedAccountLocks locks = new OrderedAccountLocks(recipientBalance, senderBalance);
        when(postingEngine.lockAccountsInOrder(2L, 1L)).thenReturn(locks);

        Balance updatedRecipient = Balance.builder()
                .balanceId(12L)
                .account(recipientAccount)
                .availableBalance(new BigDecimal("25375.0000"))
                .build();
        Balance updatedSender = Balance.builder()
                .balanceId(11L)
                .account(senderAccount)
                .availableBalance(new BigDecimal("49625.0000"))
                .build();

        when(postingEngine.applyDebit(recipientBalance, new BigDecimal("2500.0000"))).thenReturn(updatedRecipient);
        when(postingEngine.applyCredit(senderBalance, new BigDecimal("2500.0000"))).thenReturn(updatedSender);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction txn = invocation.getArgument(0);
            if ("REVERSAL_CREDIT".equals(txn.getTransactionType())) {
                txn.setTransactionId(5003L);
            } else {
                txn.setTransactionId(5004L);
            }
            return txn;
        });

        ReversalResponseDto response = reversalService.processReversal(request, 3L, "10.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("POSTED");
        assertThat(response.getReversalReferenceNo()).isEqualTo("RV-20261003-11AA22BB");
        assertThat(response.getOriginalReferenceNo()).isEqualTo("TX-20261003-8F3A21C0");
        assertThat(response.getEntries()).hasSize(2);

        // Verify sender credited (leg 1)
        assertThat(response.getEntries().get(0).getTransactionType()).isEqualTo("REVERSAL_CREDIT");
        assertThat(response.getEntries().get(0).getAccountId()).isEqualTo(1L);
        assertThat(response.getEntries().get(0).getAmount()).isEqualByComparingTo("2500.00");
        assertThat(response.getEntries().get(0).getNewBalance()).isEqualByComparingTo("49625.00");

        // Verify recipient debited (leg 2)
        assertThat(response.getEntries().get(1).getTransactionType()).isEqualTo("REVERSAL_DEBIT");
        assertThat(response.getEntries().get(1).getAccountId()).isEqualTo(2L);
        assertThat(response.getEntries().get(1).getAmount()).isEqualByComparingTo("2500.00");
        assertThat(response.getEntries().get(1).getNewBalance()).isEqualByComparingTo("25375.00");

        // Verify PostingEngine called for locking, sufficiency check, balance math, and forensic dual-write
        verify(postingEngine).lockAccountsInOrder(2L, 1L);
        verify(postingEngine).validateSufficientFunds(eq(2L), eq(new BigDecimal("27875.0000")), eq(new BigDecimal("2500.0000")));
        verify(postingEngine).applyDebit(recipientBalance, new BigDecimal("2500.0000"));
        verify(postingEngine).applyCredit(senderBalance, new BigDecimal("2500.0000"));
        verify(postingEngine).recordAudit(eq(5003L), eq(1L), eq("REVERSAL_CREDIT"), any(), any(), any(), eq(3L), eq("10.0.0.1"));
        verify(postingEngine).recordAudit(eq(5004L), eq(2L), eq("REVERSAL_DEBIT"), any(), any(), any(), eq(3L), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("Throws 404 TransactionNotFoundException when original reference is absent")
    void testProcessReversal_TransactionNotFound() {
        ReversalRequestDto request = ReversalRequestDto.builder()
                .originalReferenceNo("TX-NONEXISTENT")
                .build();

        when(transactionRepository.findByOriginalReferenceNo("TX-NONEXISTENT")).thenReturn(List.of());

        assertThatThrownBy(() -> reversalService.processReversal(request, 1L, "127.0.0.1"))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("TX-NONEXISTENT");

        verify(postingEngine, never()).lockAccountsInOrder(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Throws 409 TransactionAlreadyReversedException when original transaction already reversed")
    void testProcessReversal_AlreadyReversed() {
        ReversalRequestDto request = ReversalRequestDto.builder()
                .originalReferenceNo("TX-20261003-8F3A21C0")
                .build();

        when(transactionRepository.findByOriginalReferenceNo("TX-20261003-8F3A21C0"))
                .thenReturn(List.of(originalDebitTxn, originalCreditTxn));
        when(transactionRepository.existsByReversesTransaction_TransactionId(5001L)).thenReturn(true);

        assertThatThrownBy(() -> reversalService.processReversal(request, 1L, "127.0.0.1"))
                .isInstanceOf(TransactionAlreadyReversedException.class);

        verify(postingEngine, never()).lockAccountsInOrder(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Throws 422 InsufficientFundsException when recipient cannot absorb reversal debit")
    void testProcessReversal_InsufficientFundsOnRecipient() {
        ReversalRequestDto request = ReversalRequestDto.builder()
                .originalReferenceNo("TX-20261003-8F3A21C0")
                .build();

        when(transactionRepository.findByOriginalReferenceNo("TX-20261003-8F3A21C0"))
                .thenReturn(List.of(originalDebitTxn, originalCreditTxn));
        when(transactionRepository.existsByReversesTransaction_TransactionId(anyLong())).thenReturn(false);

        OrderedAccountLocks locks = new OrderedAccountLocks(recipientBalance, senderBalance);
        when(postingEngine.lockAccountsInOrder(2L, 1L)).thenReturn(locks);

        // Recipient only has 1000 available balance, but 2500 is needed
        recipientBalance.setAvailableBalance(new BigDecimal("1000.0000"));
        doThrow(new InsufficientFundsException(2L, new BigDecimal("1000.0000"), new BigDecimal("2500.0000")))
                .when(postingEngine).validateSufficientFunds(2L, new BigDecimal("1000.0000"), new BigDecimal("2500.0000"));

        assertThatThrownBy(() -> reversalService.processReversal(request, 1L, "127.0.0.1"))
                .isInstanceOf(InsufficientFundsException.class);

        verify(postingEngine, never()).applyDebit(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws 400 InvalidTransactionException when attempting to reverse a reversal")
    void testProcessReversal_CannotReverseReversal() {
        Transaction revTxn = Transaction.builder()
                .transactionId(6001L)
                .transactionType("REVERSAL_CREDIT")
                .referenceNo("RV-20261003-001")
                .amount(new BigDecimal("500.0000"))
                .build();

        ReversalRequestDto request = ReversalRequestDto.builder()
                .originalReferenceNo("RV-20261003-001")
                .build();

        when(transactionRepository.findByOriginalReferenceNo("RV-20261003-001")).thenReturn(List.of(revTxn));

        assertThatThrownBy(() -> reversalService.processReversal(request, 1L, "127.0.0.1"))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("Cannot reverse a reversal");
    }

    private Transaction storedReversalLeg(long id, Account account, String ref, String type, Transaction reverses,
                                          String newBalance) {
        return Transaction.builder()
                .transactionId(id)
                .account(account)
                .referenceNo(ref)
                .transactionType(type)
                .amount(new BigDecimal("2500.0000"))
                .newBalance(new BigDecimal(newBalance))
                .status("COMPLETED")
                .reversesTransaction(reverses)
                .build();
    }

    @Test
    @DisplayName("A repeat of an applied reversal returns the stored entries and moves no money")
    void testProcessReversal_RepeatReturnsTheStoredResult() {
        ReversalRequestDto request = ReversalRequestDto.builder()
                .originalReferenceNo("TX-20261003-8F3A21C0")
                .reversalReferenceNo("RV-20261003-11AA22BB")
                .reason("Customer dispute approved")
                .actorId(3L)
                .build();
        when(transactionRepository.findByOriginalReferenceNo("RV-20261003-11AA22BB")).thenReturn(List.of(
                storedReversalLeg(5003L, senderAccount, "RV-20261003-11AA22BB-C", "REVERSAL_CREDIT", originalDebitTxn, "49625.0000"),
                storedReversalLeg(5004L, recipientAccount, "RV-20261003-11AA22BB-D", "REVERSAL_DEBIT", originalCreditTxn, "25375.0000")));

        ReversalResponseDto response = reversalService.processReversal(request, 3L, "127.0.0.1");

        assertThat(response.getStatus()).isEqualTo("POSTED");
        assertThat(response.getReversalReferenceNo()).isEqualTo("RV-20261003-11AA22BB");
        assertThat(response.getOriginalReferenceNo()).isEqualTo("TX-20261003-8F3A21C0");
        assertThat(response.getEntries()).extracting("transactionId").containsExactly(5003L, 5004L);
        assertThat(response.getEntries()).extracting("transactionType").containsExactly("REVERSAL_CREDIT", "REVERSAL_DEBIT");
        assertThat(response.getEntries().get(0).getNewBalance()).isEqualByComparingTo("49625.00");
        verify(postingEngine, never()).lockAccountsInOrder(anyLong(), anyLong());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Throws 409 when the reversal reference was already used for a different original")
    void testProcessReversal_ReferenceUsedForAnotherOriginal() {
        ReversalRequestDto request = ReversalRequestDto.builder()
                .originalReferenceNo("TX-20261003-OTHER000")
                .reversalReferenceNo("RV-20261003-11AA22BB")
                .build();
        when(transactionRepository.findByOriginalReferenceNo("RV-20261003-11AA22BB")).thenReturn(List.of(
                storedReversalLeg(5003L, senderAccount, "RV-20261003-11AA22BB-C", "REVERSAL_CREDIT", originalDebitTxn, "49625.0000")));

        assertThatThrownBy(() -> reversalService.processReversal(request, 3L, "127.0.0.1"))
                .isInstanceOf(TransactionAlreadyReversedException.class);

        verify(postingEngine, never()).lockAccountsInOrder(anyLong(), anyLong());
    }

    @Test
    @DisplayName("A posting is looked up by its exact reference and leg references, never by a pattern")
    void testLegReferencesAreExact() {
        assertThat(com.group2.fse.ledger_service.repository.TransactionRepository.legReferences("TX-20261003-8F3A21C0"))
                .containsExactly("TX-20261003-8F3A21C0", "TX-20261003-8F3A21C0-D", "TX-20261003-8F3A21C0-C");

        String long36 = "TX-20261006-0123456789-0123456789-AB";
        assertThat(com.group2.fse.ledger_service.repository.TransactionRepository.legReferences(long36))
                .contains(long36, long36.substring(0, 33) + "-D", long36.substring(0, 34) + "-C")
                .allSatisfy(reference -> assertThat(reference.length()).isLessThanOrEqualTo(36));
    }
}
