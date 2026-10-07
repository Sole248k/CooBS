package com.group2.fse.transaction_service.controller;

import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.client.dto.StartReversalSagaRequest;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.event.StageEventPublisher;
import com.group2.fse.transaction_service.exception.GlobalExceptionHandler;
import com.group2.fse.transaction_service.repository.TxnStageEventRepository;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import com.group2.fse.transaction_service.security.UserPrincipal;
import com.group2.fse.transaction_service.service.IdempotencyService;
import com.group2.fse.transaction_service.service.impl.ReversalServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("T5 reversal request")
class ReversalControllerTest {

    private static final long TELLER = 16L;
    private static final UUID ORIGINAL_ID = UUID.fromString("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11");
    private static final UUID REVERSAL_ID = UUID.fromString("c4b7e2d9-5a13-4f86-b0c2-7d9e1a3f5b64");

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private TxnTransactionRepository transactionRepository;

    @Mock
    private TxnStageEventRepository stageEventRepository;

    @Mock
    private OrchestrationClient orchestrationClient;

    @Mock
    private StageEventPublisher stageEventPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReversalServiceImpl reversalService = new ReversalServiceImpl(idempotencyService, transactionRepository,
                stageEventRepository, orchestrationClient, stageEventPublisher, transactionManager);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReversalController(reversalService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(transactionRepository.findByTransactionId(ORIGINAL_ID)).thenReturn(Optional.of(original("POSTED")));
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(idempotencyService.tryAcquire(anyString(), any())).thenReturn(true);
        signInAs(TELLER, "teller_alice", "ROLE_TELLER");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(long userId, String username, String role) {
        UserPrincipal principal = UserPrincipal.create(userId, username, List.of(role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static TxnTransaction original(String status) {
        return TxnTransaction.builder()
                .transactionId(ORIGINAL_ID)
                .referenceNo("TX-20261003-8F3A21C0")
                .channel("APP")
                .customerId(1L)
                .sourceAccountId(1L)
                .destinationAccountId(2L)
                .destinationAccountNumber("ACC_10000002")
                .destinationBankCode("COOBS")
                .amount(new BigDecimal("2500.0000"))
                .currency("PHP")
                .category("TRANSFER")
                .status(status)
                .createdAt(Instant.parse("2026-10-03T11:40:00Z"))
                .build();
    }

    private static TxnTransaction acceptedReversal(UUID reverses, String status) {
        return TxnTransaction.builder()
                .transactionId(REVERSAL_ID)
                .referenceNo("RV-20261003-11AA22BB")
                .idempotencyKey(TELLER + ":rev-key-1")
                .channel("TELLER")
                .customerId(1L)
                .sourceAccountId(2L)
                .destinationAccountId(1L)
                .destinationBankCode("COOBS")
                .amount(new BigDecimal("2500.0000"))
                .currency("PHP")
                .category("TRANSFER")
                .remarks("Customer dispute approved")
                .status(status)
                .reversesTransactionId(reverses)
                .createdAt(Instant.parse("2026-10-03T11:55:00Z"))
                .build();
    }

    private static MockHttpServletRequestBuilder t5(String identifier, String key) {
        MockHttpServletRequestBuilder builder = post("/api/v1/transactions/{id}/reversal", identifier)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Customer dispute approved\"}");
        return key == null ? builder : builder.header("Idempotency-Key", key);
    }

    @Test
    @DisplayName("a teller's request is accepted with 202: a reversal transaction pointing at the original, and O2 with the teller as actor")
    void tellerRequestStartsTheReversalSaga() throws Exception {
        mockMvc.perform(t5(ORIGINAL_ID.toString(), "rev-key-1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.referenceNo", matchesPattern("^RV-\\d{8}-[A-F0-9]{8}$")))
                .andExpect(jsonPath("$.amount").value("2500.00"))
                .andExpect(jsonPath("$.reversesTransactionId").value(ORIGINAL_ID.toString()))
                .andExpect(jsonPath("$.transactionId").isString());

        ArgumentCaptor<TxnTransaction> saved = ArgumentCaptor.forClass(TxnTransaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getReversesTransactionId()).isEqualTo(ORIGINAL_ID);
        assertThat(saved.getValue().getStatus()).isEqualTo("PROCESSING");
        assertThat(saved.getValue().getSourceAccountId()).isEqualTo(2L);
        assertThat(saved.getValue().getDestinationAccountId()).isEqualTo(1L);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("2500.00");

        ArgumentCaptor<StartReversalSagaRequest> saga = ArgumentCaptor.forClass(StartReversalSagaRequest.class);
        verify(orchestrationClient).triggerReversalSagaAsync(saga.capture(), anyString());
        assertThat(saga.getValue().getTransactionId()).isEqualTo(saved.getValue().getTransactionId());
        assertThat(saga.getValue().getReversesTransactionId()).isEqualTo(ORIGINAL_ID);
        assertThat(saga.getValue().getReferenceNo()).isEqualTo(saved.getValue().getReferenceNo());
        assertThat(saga.getValue().getReason()).isEqualTo("Customer dispute approved");
        assertThat(saga.getValue().getActorId()).isEqualTo(TELLER);
    }

    @Test
    @DisplayName("the original can be named by its reference number")
    void originalCanBeNamedByReference() throws Exception {
        when(transactionRepository.findByReferenceNo("TX-20261003-8F3A21C0")).thenReturn(Optional.of(original("POSTED")));

        mockMvc.perform(t5("TX-20261003-8F3A21C0", "rev-key-1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reversesTransactionId").value(ORIGINAL_ID.toString()));
    }

    @Test
    @DisplayName("a customer cannot reverse a transfer: 403, nothing saved")
    void customerIsRefused() throws Exception {
        signInAs(1L, "john_doe", "ROLE_CUSTOMER");

        mockMvc.perform(t5(ORIGINAL_ID.toString(), "rev-key-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_ACCESS_DENIED"));

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
        verify(orchestrationClient, never()).triggerReversalSagaAsync(any(), any());
    }

    @Test
    @DisplayName("an unknown transaction is 404 TRANSACTION_NOT_FOUND")
    void unknownTransactionIsNotFound() throws Exception {
        mockMvc.perform(t5("00000000-0000-0000-0000-000000000000", "rev-key-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("a transfer that is already reversed is 409 ALREADY_REVERSED")
    void alreadyReversedIsAConflict() throws Exception {
        when(transactionRepository.findByTransactionId(ORIGINAL_ID)).thenReturn(Optional.of(original("REVERSED")));

        mockMvc.perform(t5(ORIGINAL_ID.toString(), "rev-key-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_REVERSED"));

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
    }

    @Test
    @DisplayName("a second reversal while one is in progress is 409 ALREADY_REVERSED")
    void reversalInProgressIsAConflict() throws Exception {
        when(transactionRepository.existsByReversesTransactionIdAndStatusIn(eq(ORIGINAL_ID), any())).thenReturn(true);

        mockMvc.perform(t5(ORIGINAL_ID.toString(), "rev-key-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_REVERSED"));
    }

    @Test
    @DisplayName("a failed transfer moved no money and cannot be reversed: 400")
    void failedTransferCannotBeReversed() throws Exception {
        when(transactionRepository.findByTransactionId(ORIGINAL_ID)).thenReturn(Optional.of(original("FAILED")));

        mockMvc.perform(t5(ORIGINAL_ID.toString(), "rev-key-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSACTION"));
    }

    @Test
    @DisplayName("a repeat returns the first answer with 200, and sends the saga start again while the reversal is still waiting")
    void repeatReturnsTheFirstAnswerAndResendsAWaitingSagaStart() throws Exception {
        when(transactionRepository.findByIdempotencyKey(TELLER + ":rev-key-1"))
                .thenReturn(Optional.of(acceptedReversal(ORIGINAL_ID, "PROCESSING")));

        mockMvc.perform(t5(ORIGINAL_ID.toString(), "rev-key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(REVERSAL_ID.toString()))
                .andExpect(jsonPath("$.referenceNo").value("RV-20261003-11AA22BB"))
                .andExpect(jsonPath("$.reversesTransactionId").value(ORIGINAL_ID.toString()));

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
        ArgumentCaptor<StartReversalSagaRequest> saga = ArgumentCaptor.forClass(StartReversalSagaRequest.class);
        verify(orchestrationClient).triggerReversalSagaAsync(saga.capture(), anyString());
        assertThat(saga.getValue().getTransactionId()).isEqualTo(REVERSAL_ID);
    }

    @Test
    @DisplayName("a repeat of a reversal that has finished starts nothing")
    void repeatOfAFinishedReversalStartsNothing() throws Exception {
        when(transactionRepository.findByIdempotencyKey(TELLER + ":rev-key-1"))
                .thenReturn(Optional.of(acceptedReversal(ORIGINAL_ID, "POSTED")));
        when(transactionRepository.findByTransactionId(ORIGINAL_ID)).thenReturn(Optional.of(original("REVERSED")));

        mockMvc.perform(t5(ORIGINAL_ID.toString(), "rev-key-1")).andExpect(status().isOk());

        verify(orchestrationClient, never()).triggerReversalSagaAsync(any(), any());
    }

    @Test
    @DisplayName("the same key for a different transfer is 409 DUPLICATE_IDEMPOTENCY_KEY")
    void sameKeyForAnotherTransferIsRefused() throws Exception {
        when(transactionRepository.findByIdempotencyKey(TELLER + ":rev-key-1"))
                .thenReturn(Optional.of(acceptedReversal(UUID.randomUUID(), "POSTED")));

        mockMvc.perform(t5(ORIGINAL_ID.toString(), "rev-key-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_IDEMPOTENCY_KEY"));
    }

    @Test
    @DisplayName("Idempotency-Key is required: 400 without it")
    void missingKeyIsABadRequest() throws Exception {
        mockMvc.perform(t5(ORIGINAL_ID.toString(), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_PARAMETERS"));
    }
}
