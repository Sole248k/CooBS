package com.group2.fse.transaction_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group2.fse.transaction_service.client.AccountLookupUnavailableException;
import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.client.dto.AccountLookupResponse;
import com.group2.fse.transaction_service.client.dto.StartTransferSagaRequest;
import com.group2.fse.transaction_service.dto.TransferRequestDto;
import com.group2.fse.transaction_service.entity.InterbankMessage;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.event.StageEventPublisher;
import com.group2.fse.transaction_service.exception.GlobalExceptionHandler;
import com.group2.fse.transaction_service.interbank.security.InterbankSecurityProperties;
import com.group2.fse.transaction_service.repository.InterbankMessageRepository;
import com.group2.fse.transaction_service.repository.TxnStageEventRepository;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import com.group2.fse.transaction_service.security.UserPrincipal;
import com.group2.fse.transaction_service.service.IdempotencyService;
import com.group2.fse.transaction_service.service.impl.TransferServiceImpl;
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
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("T1 transfer ingress: ownership, account checks and idempotency")
class TransferControllerTest {

    private static final long JOHN = 1L;
    private static final long MARIA = 2L;
    private static final long TELLER = 16L;

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

    @Mock
    private InterbankMessageRepository interbankMessages;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        InterbankSecurityProperties partners = new InterbankSecurityProperties();
        InterbankSecurityProperties.PartnerConfig bankB = new InterbankSecurityProperties.PartnerConfig();
        bankB.setBankCode("BANKB");
        bankB.setSharedSecret("test-shared-secret-2026");
        bankB.setSettlementAccountId(9005L);
        partners.getPartners().put("BANKB", bankB);
        TransferServiceImpl transferService = new TransferServiceImpl(idempotencyService, transactionRepository,
                stageEventRepository, orchestrationClient, stageEventPublisher, transactionManager,
                partners, interbankMessages);
        mockMvc = MockMvcBuilders.standaloneSetup(new TransferController(transferService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // John owns account 1, Maria owns account 2, and no request has been seen before.
        when(orchestrationClient.findAccountById(1L)).thenReturn(Optional.of(account(1L, JOHN, "ACC_10000001")));
        when(orchestrationClient.findAccountByNumber("ACC_10000002"))
                .thenReturn(Optional.of(account(2L, MARIA, "ACC_10000002")));
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(idempotencyService.tryAcquire(anyString(), any())).thenReturn(true);
        signInAs(JOHN, "john_doe", "ROLE_CUSTOMER");
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

    private static AccountLookupResponse account(Long accountId, Long customerId, String number) {
        AccountLookupResponse account = new AccountLookupResponse();
        account.setAccountId(accountId);
        account.setCustomerId(customerId);
        account.setAccountNumber(number);
        account.setStatus("ACTIVE");
        return account;
    }

    private static TransferRequestDto transfer(String amount) {
        return TransferRequestDto.builder()
                .sourceAccountId(1L)
                .destinationAccountNumber("ACC_10000002")
                .destinationBankCode("COOBS")
                .amount(new BigDecimal(amount))
                .currency("PHP")
                .category("TRANSFER")
                .remarks("Lunch money")
                .build();
    }

    private MockHttpServletRequestBuilder t1(TransferRequestDto request, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/transactions/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
        return idempotencyKey == null ? builder : builder.header("Idempotency-Key", idempotencyKey);
    }

    private static TxnTransaction acceptedEarlier(long customerId, String amount) {
        return TxnTransaction.builder()
                .transactionId(UUID.fromString("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11"))
                .referenceNo("TX-20261003-8F3A21C0")
                .idempotencyKey(customerId + ":idemp-key-12345")
                .channel("APP")
                .customerId(customerId)
                .sourceAccountId(1L)
                .destinationAccountId(2L)
                .destinationAccountNumber("ACC_10000002")
                .destinationBankCode("COOBS")
                .amount(new BigDecimal(amount))
                .currency("PHP")
                .category("TRANSFER")
                .status("POSTED")
                .createdAt(Instant.parse("2026-10-03T11:40:00Z"))
                .build();
    }

    @Test
    @DisplayName("a customer sending from their own account gets 202, the amount as a string, and a saga for the right people")
    void ownTransferIsAcceptedAndTheSagaCarriesTheResolvedAccounts() throws Exception {
        mockMvc.perform(t1(transfer("2500.00"), "idemp-key-12345").header("X-Correlation-ID", "corr-e2e-01"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.referenceNo", matchesPattern("^TX-\\d{8}-[A-F0-9]{8}$")))
                .andExpect(jsonPath("$.amount").value("2500.00"))
                .andExpect(jsonPath("$.currency").value("PHP"))
                .andExpect(jsonPath("$.transactionId").isString())
                .andExpect(jsonPath("$.timelineUrl", startsWith("/api/v1/transactions/")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.replayed").doesNotExist());

        ArgumentCaptor<TxnTransaction> saved = ArgumentCaptor.forClass(TxnTransaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getCustomerId()).isEqualTo(JOHN);
        assertThat(saved.getValue().getDestinationAccountId()).isEqualTo(2L);
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("1:idemp-key-12345");

        ArgumentCaptor<StartTransferSagaRequest> saga = ArgumentCaptor.forClass(StartTransferSagaRequest.class);
        verify(orchestrationClient).triggerTransferSagaAsync(saga.capture(), eq("corr-e2e-01"));
        assertThat(saga.getValue().getCustomerId()).isEqualTo(JOHN);
        assertThat(saga.getValue().getSourceAccountId()).isEqualTo(1L);
        assertThat(saga.getValue().getDestinationAccountId()).isEqualTo(2L);
        assertThat(saga.getValue().getActorId()).isEqualTo(JOHN);
        assertThat(saga.getValue().getActorType()).isEqualTo("CUSTOMER");
        verify(idempotencyService).release("1:idemp-key-12345");
    }

    @Test
    @DisplayName("a customer cannot send from an account that belongs to someone else: 403 ACCOUNT_NOT_OWNED, nothing saved")
    void anotherCustomersAccountIsRefused() throws Exception {
        signInAs(MARIA, "maria_santos", "ROLE_CUSTOMER");

        mockMvc.perform(t1(transfer("2500.00"), "idemp-key-12345"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_OWNED"));

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
        verify(orchestrationClient, never()).triggerTransferSagaAsync(any(), any());
    }

    @Test
    @DisplayName("a teller may send from a member's account; the saga runs for the owner with the teller as actor")
    void tellerMaySendFromAMembersAccount() throws Exception {
        signInAs(TELLER, "teller_alice", "ROLE_TELLER");

        mockMvc.perform(t1(transfer("2500.00"), "idemp-key-12345")).andExpect(status().isAccepted());

        ArgumentCaptor<StartTransferSagaRequest> saga = ArgumentCaptor.forClass(StartTransferSagaRequest.class);
        verify(orchestrationClient).triggerTransferSagaAsync(saga.capture(), anyString());
        assertThat(saga.getValue().getCustomerId()).isEqualTo(JOHN);
        assertThat(saga.getValue().getActorId()).isEqualTo(TELLER);
        assertThat(saga.getValue().getActorType()).isEqualTo("STAFF");
        assertThat(saga.getValue().getChannel()).isEqualTo("TELLER");
    }

    @Test
    @DisplayName("an unknown destination is 404 ACCOUNT_NOT_FOUND; no account is guessed")
    void unknownDestinationIsNotFound() throws Exception {
        when(orchestrationClient.findAccountByNumber("ACC_10000002")).thenReturn(Optional.empty());

        mockMvc.perform(t1(transfer("2500.00"), "idemp-key-12345"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
        verify(orchestrationClient, never()).triggerTransferSagaAsync(any(), any());
    }

    @Test
    @DisplayName("when account-service gives no answer the request is 503 ACCOUNT_UNAVAILABLE; no account is guessed")
    void accountServiceDownStopsTheTransfer() throws Exception {
        when(orchestrationClient.findAccountById(1L))
                .thenThrow(new AccountLookupUnavailableException("connection refused", null));

        mockMvc.perform(t1(transfer("2500.00"), "idemp-key-12345"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_UNAVAILABLE"));

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
        verify(orchestrationClient, never()).triggerTransferSagaAsync(any(), any());
    }

    @Test
    @DisplayName("a repeat of an accepted request returns the first answer with 200 and starts nothing")
    void repeatReturnsTheFirstAnswer() throws Exception {
        when(transactionRepository.findByIdempotencyKey("1:idemp-key-12345"))
                .thenReturn(Optional.of(acceptedEarlier(JOHN, "2500.0000")));

        mockMvc.perform(t1(transfer("2500.00"), "idemp-key-12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11"))
                .andExpect(jsonPath("$.referenceNo").value("TX-20261003-8F3A21C0"))
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.amount").value("2500.00"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-03T11:40:00Z"));

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
        verify(orchestrationClient, never()).triggerTransferSagaAsync(any(), any());
    }

    @Test
    @DisplayName("the same key for a different transfer is 409 DUPLICATE_IDEMPOTENCY_KEY")
    void sameKeyForADifferentTransferIsRefused() throws Exception {
        when(transactionRepository.findByIdempotencyKey("1:idemp-key-12345"))
                .thenReturn(Optional.of(acceptedEarlier(JOHN, "2500.0000")));

        mockMvc.perform(t1(transfer("9000.00"), "idemp-key-12345"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_IDEMPOTENCY_KEY"));
    }

    @Test
    @DisplayName("a key is scoped to its caller: another customer's key is not a repeat")
    void anotherCallersKeyIsNotARepeat() throws Exception {
        when(transactionRepository.findByIdempotencyKey("2:idemp-key-12345"))
                .thenReturn(Optional.of(acceptedEarlier(MARIA, "2500.0000")));

        mockMvc.perform(t1(transfer("2500.00"), "idemp-key-12345")).andExpect(status().isAccepted());

        verify(transactionRepository, atLeastOnce()).findByIdempotencyKey("1:idemp-key-12345");
        verify(transactionRepository).save(any(TxnTransaction.class));
    }

    @Test
    @DisplayName("a second request while the first is still being accepted is 409 CONCURRENT_TRANSACTION_IN_FLIGHT")
    void duplicateInFlightIsAConflict() throws Exception {
        when(idempotencyService.tryAcquire(eq("1:dup-key-999"), any())).thenReturn(false);

        mockMvc.perform(t1(transfer("2500.00"), "dup-key-999"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_TRANSACTION_IN_FLIGHT"))
                .andExpect(jsonPath("$.title").value("Transaction Already In Progress"))
                .andExpect(jsonPath("$.status").value(409));

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
    }

    @Test
    @DisplayName("Idempotency-Key is required: 400 INVALID_REQUEST_PARAMETERS without it")
    void missingIdempotencyKeyIsABadRequest() throws Exception {
        mockMvc.perform(t1(transfer("2500.00"), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_PARAMETERS"));

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
    }

    @Test
    @DisplayName("a transfer to a partner bank is accepted: no lookup of the account there, the settlement account for the saga, and the message recorded")
    void partnerBankIsAccepted() throws Exception {
        TransferRequestDto request = transfer("2500.00");
        request.setDestinationBankCode("bankb");
        request.setDestinationAccountNumber("1000-2000-3000");
        request.setBeneficiaryName("Ana Lim");

        mockMvc.perform(t1(request, "idemp-key-12345"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("INITIATED"));

        // The account is at the other bank; ours cannot know it.
        verify(orchestrationClient, never()).findAccountByNumber(anyString());
        ArgumentCaptor<TxnTransaction> saved = ArgumentCaptor.forClass(TxnTransaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getDestinationBankCode()).isEqualTo("BANKB");
        assertThat(saved.getValue().getDestinationAccountNumber()).isEqualTo("1000-2000-3000");
        assertThat(saved.getValue().getDestinationAccountId()).as("the partner's settlement account").isEqualTo(9005L);

        ArgumentCaptor<InterbankMessage> message = ArgumentCaptor.forClass(InterbankMessage.class);
        verify(interbankMessages).save(message.capture());
        assertThat(message.getValue().getMessageId()).isEqualTo(saved.getValue().getTransactionId());
        assertThat(message.getValue().getDirection()).isEqualTo("OUTBOUND");
        assertThat(message.getValue().getPartnerBankCode()).isEqualTo("BANKB");
        assertThat(message.getValue().getCounterpartyAccount()).isEqualTo("1000-2000-3000");
        assertThat(message.getValue().getCounterpartyName()).isEqualTo("Ana Lim");
        assertThat(message.getValue().getStatus()).as("nothing is sent from T1").isEqualTo("CREATED");

        ArgumentCaptor<StartTransferSagaRequest> saga = ArgumentCaptor.forClass(StartTransferSagaRequest.class);
        verify(orchestrationClient).triggerTransferSagaAsync(saga.capture(), any());
        assertThat(saga.getValue().getDestinationBankCode()).isEqualTo("BANKB");
        assertThat(saga.getValue().getDestinationAccountId()).isEqualTo(9005L);
    }

    @Test
    @DisplayName("a transfer inside our own bank records no inter-bank message")
    void ownBankRecordsNoMessage() throws Exception {
        mockMvc.perform(t1(transfer("2500.00"), "idemp-key-12345")).andExpect(status().isAccepted());

        verify(interbankMessages, never()).save(any(InterbankMessage.class));
    }

    @Test
    @DisplayName("an amount a partner bank cannot carry (more than two decimals) is refused before anything is saved")
    void partnerBankAmountHasTwoDecimals() throws Exception {
        TransferRequestDto request = transfer("2500.005");
        request.setDestinationBankCode("BANKB");
        request.setDestinationAccountNumber("1000-2000-3000");

        mockMvc.perform(t1(request, "idemp-key-12345")).andExpect(status().isBadRequest());

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
    }

    @Test
    @DisplayName("a transfer to a bank that is not a configured partner is refused with 422 INTERBANK_REJECTED")
    void anotherBankIsRefused() throws Exception {
        TransferRequestDto request = transfer("2500.00");
        request.setDestinationBankCode("PARTNERBANK");

        mockMvc.perform(t1(request, "idemp-key-12345"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INTERBANK_REJECTED"));

        verify(transactionRepository, never()).save(any(TxnTransaction.class));
    }

    @Test
    @DisplayName("an invalid amount is 400")
    void invalidAmountIsABadRequest() throws Exception {
        mockMvc.perform(t1(transfer("-100.00"), "idemp-key-12345")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a missing source account is 400")
    void missingSourceAccountIsABadRequest() throws Exception {
        TransferRequestDto request = transfer("500.00");
        request.setSourceAccountId(null);

        mockMvc.perform(t1(request, "idemp-key-12345")).andExpect(status().isBadRequest());
    }
}
