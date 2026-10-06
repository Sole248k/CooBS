package com.group2.fse.ledger_service.reversal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.ledger_service.config.JacksonConfig;
import com.group2.fse.ledger_service.exception.GlobalExceptionHandler;
import com.group2.fse.ledger_service.exception.InsufficientFundsException;
import com.group2.fse.ledger_service.exception.TransactionAlreadyReversedException;
import com.group2.fse.ledger_service.exception.TransactionNotFoundException;
import com.group2.fse.ledger_service.reversal.controller.ReversalController;
import com.group2.fse.ledger_service.reversal.dto.ReversalEntryDto;
import com.group2.fse.ledger_service.reversal.dto.ReversalRequestDto;
import com.group2.fse.ledger_service.reversal.dto.ReversalResponseDto;
import com.group2.fse.ledger_service.reversal.service.ReversalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Reversal Controller Unit Tests (Handshake H3 L7)")
class ReversalControllerTest {

    @Mock
    private ReversalService reversalService;

    @InjectMocks
    private ReversalController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /internal/v1/ledger/reversals: Returns 201 Created adhering to Handshake H3 L7 contract")
    void testReverse_Internal_Success() throws Exception {
        ReversalRequestDto requestDto = ReversalRequestDto.builder()
                .originalReferenceNo("TX-20261003-8F3A21C0")
                .reversalReferenceNo("RV-20261003-11AA22BB")
                .reason("Customer dispute approved")
                .actorId(3L)
                .build();

        ReversalResponseDto responseDto = ReversalResponseDto.builder()
                .reversalReferenceNo("RV-20261003-11AA22BB")
                .originalReferenceNo("TX-20261003-8F3A21C0")
                .status("POSTED")
                .entries(List.of(
                        ReversalEntryDto.builder()
                                .transactionId(5003L)
                                .accountId(1L)
                                .transactionType("REVERSAL_CREDIT")
                                .amount(new BigDecimal("2500.00"))
                                .newBalance(new BigDecimal("49625.00"))
                                .build(),
                        ReversalEntryDto.builder()
                                .transactionId(5004L)
                                .accountId(2L)
                                .transactionType("REVERSAL_DEBIT")
                                .amount(new BigDecimal("2500.00"))
                                .newBalance(new BigDecimal("25375.00"))
                                .build()
                ))
                .timestamp(Instant.parse("2026-10-03T12:10:00Z"))
                .reversalId("RV-20261003-11AA22BB")
                .originalTransactionId("TX-20261003-8F3A21C0")
                .reversedAmount(new BigDecimal("2500.00"))
                .currency("PHP")
                .build();

        when(reversalService.processReversal(any(ReversalRequestDto.class), any(), any()))
                .thenReturn(responseDto);

        mockMvc.perform(post("/internal/v1/ledger/reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversalReferenceNo").value("RV-20261003-11AA22BB"))
                .andExpect(jsonPath("$.originalReferenceNo").value("TX-20261003-8F3A21C0"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.entries[0].transactionId").value(5003))
                .andExpect(jsonPath("$.entries[0].accountId").value(1))
                .andExpect(jsonPath("$.entries[0].transactionType").value("REVERSAL_CREDIT"))
                .andExpect(jsonPath("$.entries[0].amount").value(2500.00))
                .andExpect(jsonPath("$.entries[0].newBalance").value(49625.00))
                .andExpect(jsonPath("$.entries[1].transactionId").value(5004))
                .andExpect(jsonPath("$.entries[1].accountId").value(2))
                .andExpect(jsonPath("$.entries[1].transactionType").value("REVERSAL_DEBIT"))
                .andExpect(jsonPath("$.entries[1].amount").value(2500.00))
                .andExpect(jsonPath("$.entries[1].newBalance").value(25375.00));
    }

    @Test
    @DisplayName("POST /api/v1/ledger/reversals: Supports dual public endpoint path")
    void testReverse_PublicAlias_Success() throws Exception {
        ReversalRequestDto requestDto = ReversalRequestDto.builder()
                .originalReferenceNo("TX-20261003-8F3A21C0")
                .build();

        ReversalResponseDto responseDto = ReversalResponseDto.builder()
                .reversalReferenceNo("RV-001")
                .originalReferenceNo("TX-20261003-8F3A21C0")
                .status("POSTED")
                .entries(List.of())
                .timestamp(Instant.now())
                .build();

        when(reversalService.processReversal(any(ReversalRequestDto.class), any(), any()))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/ledger/reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"));
    }

    @Test
    @DisplayName("POST /internal/v1/ledger/reversals: Returns 404 when original transaction does not exist")
    void testReverse_NotFound_Returns404() throws Exception {
        ReversalRequestDto requestDto = ReversalRequestDto.builder()
                .originalReferenceNo("TX-MISSING")
                .build();

        when(reversalService.processReversal(any(ReversalRequestDto.class), any(), any()))
                .thenThrow(new TransactionNotFoundException("TX-MISSING"));

        mockMvc.perform(post("/internal/v1/ledger/reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /internal/v1/ledger/reversals: Returns 409 Conflict when already reversed")
    void testReverse_AlreadyReversed_Returns409() throws Exception {
        ReversalRequestDto requestDto = ReversalRequestDto.builder()
                .originalReferenceNo("TX-ALREADY-REVERSED")
                .build();

        when(reversalService.processReversal(any(ReversalRequestDto.class), any(), any()))
                .thenThrow(new TransactionAlreadyReversedException("TX-ALREADY-REVERSED"));

        mockMvc.perform(post("/internal/v1/ledger/reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("ALREADY_REVERSED"));
    }

    @Test
    @DisplayName("POST /internal/v1/ledger/reversals: Returns 422 Unprocessable Entity when recipient has insufficient funds")
    void testReverse_InsufficientFunds_Returns422() throws Exception {
        ReversalRequestDto requestDto = ReversalRequestDto.builder()
                .originalReferenceNo("TX-123")
                .build();

        when(reversalService.processReversal(any(ReversalRequestDto.class), any(), any()))
                .thenThrow(new InsufficientFundsException(2L, new BigDecimal("100.00"), new BigDecimal("2500.00")));

        mockMvc.perform(post("/internal/v1/ledger/reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"));
    }
}
