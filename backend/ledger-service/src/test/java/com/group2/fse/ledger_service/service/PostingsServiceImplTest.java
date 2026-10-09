package com.group2.fse.ledger_service.service;

import com.group2.fse.ledger_service.dto.PostingsRequestDto;
import com.group2.fse.ledger_service.dto.PostingsRequestDto.PostingItem;
import com.group2.fse.ledger_service.dto.PostingsResponseDto;
import com.group2.fse.ledger_service.dto.TransferRequestDto;
import com.group2.fse.ledger_service.dto.TransferResponseDto;
import com.group2.fse.ledger_service.exception.AccountNotFoundException;
import com.group2.fse.ledger_service.exception.DualWriteAuditException;
import com.group2.fse.ledger_service.exception.InsufficientFundsException;
import com.group2.fse.ledger_service.repository.TransactionRepository;
import com.group2.fse.ledger_service.repository.TransactionRepository.Leg;
import com.group2.fse.ledger_service.service.impl.PostingsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostingsServiceImpl: L8 postings")
class PostingsServiceImplTest {

    private static final String REF = "IB-20261008-000045";
    private static final String CLIENT_IP = "10.240.0.7";

    @Mock
    private AccountBalanceService accountBalanceService;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private PostingsServiceImpl service;

    private static PostingItem item(String ref, long debit, long credit, String amount) {
        return PostingItem.builder().referenceNo(ref).debitAccountId(debit).creditAccountId(credit)
                .amount(new BigDecimal(amount)).remarks("test").build();
    }

    private static PostingsRequestDto request(PostingItem... items) {
        return PostingsRequestDto.builder().transactionId(UUID.randomUUID()).actorId(7L).postings(List.of(items)).build();
    }

    private static Leg leg(String referenceNo, long accountId, String amount) {
        return new Leg() {
            @Override
            public String getReferenceNo() {
                return referenceNo;
            }

            @Override
            public Long getAccountId() {
                return accountId;
            }

            @Override
            public BigDecimal getAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    private void journalIsEmpty() {
        when(transactionRepository.findLegsByReferenceNoIn(anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("A new posting is posted once, with the caller's actor and address, and counted")
    void postsANewPosting() {
        journalIsEmpty();
        when(accountBalanceService.executeTransfer(any(TransferRequestDto.class), eq(7L), eq(CLIENT_IP)))
                .thenReturn(TransferResponseDto.builder().status("COMPLETED").build());

        PostingsResponseDto response = service.post(request(item(REF, 9005, 4, "150")), CLIENT_IP);

        ArgumentCaptor<TransferRequestDto> sent = ArgumentCaptor.forClass(TransferRequestDto.class);
        verify(accountBalanceService).executeTransfer(sent.capture(), eq(7L), eq(CLIENT_IP));
        assertThat(sent.getValue().getSourceAccountId()).isEqualTo(9005L);
        assertThat(sent.getValue().getDestinationAccountId()).isEqualTo(4L);
        assertThat(sent.getValue().getReferenceNo()).isEqualTo(REF);
        assertThat(response.getPosted()).isEqualTo(1);
        assertThat(response.getFailed()).isZero();
        // Money leaves with at least two decimals.
        assertThat(response.getTotalAmount().toPlainString()).isEqualTo("150.00");
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("POSTED");
        assertThat(response.getResults().get(0).getErrorCode()).isNull();
    }

    @Test
    @DisplayName("A repeat of a posting that is already in the journal is POSTED and not posted again")
    void aRepeatIsNotPostedTwice() {
        when(transactionRepository.findLegsByReferenceNoIn(List.of(REF + "-D", REF + "-C")))
                .thenReturn(List.of(leg(REF + "-D", 9005, "150.0000"), leg(REF + "-C", 4, "150.0000")));

        PostingsResponseDto response = service.post(request(item(REF, 9005, 4, "150.00")), CLIENT_IP);

        verify(accountBalanceService, never()).executeTransfer(any(), any(), any());
        assertThat(response.getPosted()).isEqualTo(1);
        assertThat(response.getFailed()).isZero();
        assertThat(response.getTotalAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("A reference already used for another amount or account is refused, never posted over")
    void aReusedReferenceIsRefused() {
        when(transactionRepository.findLegsByReferenceNoIn(anyCollection()))
                .thenReturn(List.of(leg(REF + "-D", 9005, "150.0000"), leg(REF + "-C", 4, "150.0000")));

        PostingsResponseDto otherAmount = service.post(request(item(REF, 9005, 4, "900.00")), CLIENT_IP);
        PostingsResponseDto otherAccount = service.post(request(item(REF, 9005, 5, "150.00")), CLIENT_IP);

        verify(accountBalanceService, never()).executeTransfer(any(), any(), any());
        assertThat(otherAmount.getResults().get(0).getErrorCode()).isEqualTo("DUPLICATE_IDEMPOTENCY_KEY");
        assertThat(otherAccount.getResults().get(0).getErrorCode()).isEqualTo("DUPLICATE_IDEMPOTENCY_KEY");
        assertThat(otherAmount.getPosted()).isZero();
        assertThat(otherAmount.getTotalAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("A refused posting carries a contract error code, and the postings after it still post")
    void aRefusalDoesNotStopTheOthers() {
        journalIsEmpty();
        when(accountBalanceService.executeTransfer(any(TransferRequestDto.class), any(), any()))
                .thenThrow(new InsufficientFundsException(9002L, new BigDecimal("1.00"), new BigDecimal("4.08")))
                .thenThrow(new AccountNotFoundException(404L))
                .thenReturn(TransferResponseDto.builder().status("COMPLETED").build());

        PostingsResponseDto response = service.post(request(
                item("EOD-1", 9002, 1, "4.08"), item("EOD-2", 9002, 404, "1.00"), item("EOD-3", 9002, 3, "1.5")), CLIENT_IP);

        assertThat(response.getPosted()).isEqualTo(1);
        assertThat(response.getFailed()).isEqualTo(2);
        assertThat(response.getResults()).extracting(PostingsResponseDto.PostingResult::getErrorCode)
                .containsExactly("INSUFFICIENT_FUNDS", "ACCOUNT_NOT_FOUND", null);
        // Only what was posted is in the total.
        assertThat(response.getTotalAmount().toPlainString()).isEqualTo("1.50");
    }

    @Test
    @DisplayName("A failure of the ledger itself is thrown, not reported as a refused posting")
    void anInfrastructureFailureIsNotARefusal() {
        journalIsEmpty();
        when(accountBalanceService.executeTransfer(any(TransferRequestDto.class), any(), any()))
                .thenThrow(new DualWriteAuditException("audit store is down", new RuntimeException("connection refused")));

        assertThatThrownBy(() -> service.post(request(item(REF, 9005, 4, "150.00")), CLIENT_IP))
                .isInstanceOf(DualWriteAuditException.class);
    }

    @Test
    @DisplayName("Two requests with one reference at the same moment: the loser reads the journal and answers POSTED")
    void aRaceOnTheReferenceEndsAsARepeat() {
        when(transactionRepository.findLegsByReferenceNoIn(anyCollection()))
                .thenReturn(List.of())
                .thenReturn(List.of(leg(REF + "-D", 9005, "150.0000"), leg(REF + "-C", 4, "150.0000")));
        when(accountBalanceService.executeTransfer(any(TransferRequestDto.class), any(), any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint on reference_no"));

        PostingsResponseDto response = service.post(request(item(REF, 9005, 4, "150.00")), CLIENT_IP);

        assertThat(response.getPosted()).isEqualTo(1);
        assertThat(response.getFailed()).isZero();
    }

    @Test
    @DisplayName("A constraint failure with nothing in the journal is thrown")
    void aConstraintFailureThatIsNotARepeatIsThrown() {
        journalIsEmpty();
        when(accountBalanceService.executeTransfer(any(TransferRequestDto.class), any(), any()))
                .thenThrow(new DataIntegrityViolationException("chk_ora_ledger_ge_avail"));

        assertThatThrownBy(() -> service.post(request(item(REF, 9005, 4, "150.00")), CLIENT_IP))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Debit and credit on the same account are refused without touching the ledger")
    void sameAccountOnBothSidesIsRefused() {
        PostingsResponseDto response = service.post(request(item(REF, 4, 4, "150.00")), CLIENT_IP);

        verify(accountBalanceService, never()).executeTransfer(any(), any(), any());
        assertThat(response.getResults().get(0).getErrorCode()).isEqualTo("INVALID_TRANSACTION");
    }
}
