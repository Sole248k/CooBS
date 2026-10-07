package com.group2.fse.transaction_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group2.fse.transaction_service.dto.TransferInitiateResponseDto;
import com.group2.fse.transaction_service.dto.TransferRequestDto;
import com.group2.fse.transaction_service.exception.GlobalExceptionHandler;
import com.group2.fse.transaction_service.service.IdempotencyService;
import com.group2.fse.transaction_service.service.TransferService;
import com.group2.fse.transaction_service.service.impl.TransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import com.group2.fse.transaction_service.repository.TxnStageEventRepository;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferController Standalone Unit Tests")
class TransferControllerTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private TxnTransactionRepository transactionRepository;

    @Mock
    private TxnStageEventRepository stageEventRepository;

    @Mock
    private com.group2.fse.transaction_service.client.OrchestrationClient orchestrationClient;

    @Mock
    private com.group2.fse.transaction_service.event.StageEventPublisher stageEventPublisher;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TransferService transferService = new TransferServiceImpl(
                idempotencyService, objectMapper, transactionRepository, stageEventRepository, orchestrationClient, stageEventPublisher);
        TransferController transferController = new TransferController(transferService);
        mockMvc = MockMvcBuilders.standaloneSetup(transferController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/transactions/transfers returns immediate 202 Accepted with TX- reference")
    void testInitiateTransferSuccess() throws Exception {
        when(idempotencyService.tryAcquire(eq("idemp-key-12345"), any())).thenReturn(true);

        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(1L)
                .destinationAccountNumber("ACC_10000002")
                .destinationBankCode("COOBS")
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .category("TRANSFER")
                .remarks("Lunch money")
                .build();

        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idemp-key-12345")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.referenceNo", startsWith("TX-")))
                .andExpect(jsonPath("$.referenceNo", matchesPattern("^TX-\\d{8}-[A-F0-9]{8}$")))
                .andExpect(jsonPath("$.amount").value(2500.00))
                .andExpect(jsonPath("$.currency").value("PHP"))
                .andExpect(jsonPath("$.transactionId").isString())
                .andExpect(jsonPath("$.timelineUrl", startsWith("/api/v1/transactions/")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("Duplicate in-flight request returns 409 Conflict (CONCURRENT_TRANSACTION_IN_FLIGHT)")
    void testDuplicateInFlightReturns409Conflict() throws Exception {
        when(idempotencyService.tryAcquire(eq("dup-key-999"), any())).thenReturn(false);

        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(1L)
                .destinationAccountNumber("ACC_10000002")
                .amount(new BigDecimal("2500.00"))
                .build();

        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "dup-key-999")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_TRANSACTION_IN_FLIGHT"))
                .andExpect(jsonPath("$.title").value("Transaction Already In Progress"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Second request with same Idempotency-Key returns 409 Conflict")
    void testSecondRequestWithSameKeyReturns409Conflict() throws Exception {
        when(idempotencyService.tryAcquire(eq("same-key-222"), any())).thenReturn(false);

        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(1L)
                .destinationAccountNumber("ACC_10000002")
                .amount(new BigDecimal("2500.00"))
                .build();

        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "same-key-222")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_TRANSACTION_IN_FLIGHT"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/transfers with invalid amount returns 400 Bad Request")
    void testInitiateTransferInvalidAmount() throws Exception {
        TransferRequestDto request = TransferRequestDto.builder()
                .sourceAccountId(1L)
                .destinationAccountNumber("ACC_10000002")
                .amount(new BigDecimal("-100.00"))
                .build();

        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/transactions/transfers with missing source account returns 400 Bad Request")
    void testInitiateTransferMissingSourceAccount() throws Exception {
        TransferRequestDto request = TransferRequestDto.builder()
                .destinationAccountNumber("ACC_10000002")
                .amount(new BigDecimal("500.00"))
                .build();

        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
