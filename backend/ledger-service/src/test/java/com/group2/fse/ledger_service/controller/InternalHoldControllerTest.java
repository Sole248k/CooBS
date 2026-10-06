package com.group2.fse.ledger_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.ledger_service.dto.*;
import com.group2.fse.ledger_service.exception.GlobalExceptionHandler;
import com.group2.fse.ledger_service.exception.HoldNotFoundException;
import com.group2.fse.ledger_service.service.HoldService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalHoldController Unit Tests (FIN-303 L3 - L6)")
class InternalHoldControllerTest {

    @Mock
    private HoldService holdService;

    @InjectMocks
    private InternalHoldController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new com.group2.fse.ledger_service.config.JacksonConfig().objectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("L3: POST /internal/v1/ledger/holds returns 201 Created with reserved hold")
    void shouldReturn201OnPlaceHold() throws Exception {
        PlaceHoldRequestDto requestDto = PlaceHoldRequestDto.builder()
                .referenceNo("TX-20261003-8F3A21C0")
                .accountId(1L)
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .actorId(1L)
                .build();

        PlaceHoldResponseDto responseDto = PlaceHoldResponseDto.builder()
                .holdId(9001L)
                .referenceNo("TX-20261003-8F3A21C0")
                .accountId(1L)
                .amount(new BigDecimal("2500.00"))
                .status("RESERVED")
                .availableBalance(new BigDecimal("47125.00"))
                .ledgerBalance(new BigDecimal("49625.00"))
                .createdAt("2026-10-03T11:40:00.200Z")
                .build();

        when(holdService.placeHold(any(PlaceHoldRequestDto.class), any())).thenReturn(responseDto);

        mockMvc.perform(post("/internal/v1/ledger/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idemp-hold-001")
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId").value(9001))
                .andExpect(jsonPath("$.referenceNo").value("TX-20261003-8F3A21C0"))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.amount").value(2500.00))
                .andExpect(jsonPath("$.availableBalance").value(47125.00))
                .andExpect(jsonPath("$.ledgerBalance").value(49625.00));
    }

    @Test
    @DisplayName("L4: POST /internal/v1/ledger/holds/{holdId}/capture returns 200 OK")
    void shouldReturn200OnCaptureHold() throws Exception {
        CaptureHoldRequestDto requestDto = CaptureHoldRequestDto.builder()
                .destinationAccountId(2L)
                .remarks("Lunch money")
                .actorId(1L)
                .clientIp("203.0.113.10")
                .build();

        CaptureHoldResponseDto responseDto = CaptureHoldResponseDto.builder()
                .holdId(9001L)
                .status("POSTED")
                .transferReference("TX-20261003-8F3A21C0")
                .debitTransactionId(5001L)
                .creditTransactionId(5002L)
                .sourceAccountId(1L)
                .destinationAccountId(2L)
                .amount(new BigDecimal("2500.00"))
                .sourceNewBalance(new BigDecimal("47125.00"))
                .destinationNewBalance(new BigDecimal("27875.00"))
                .auditHash("a3f5b8e9c1d0")
                .timestamp("2026-10-03T11:40:00.900Z")
                .build();

        when(holdService.captureHold(eq(9001L), any(CaptureHoldRequestDto.class), any())).thenReturn(responseDto);

        mockMvc.perform(post("/internal/v1/ledger/holds/9001/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idemp-cap-001")
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdId").value(9001))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.transferReference").value("TX-20261003-8F3A21C0"))
                .andExpect(jsonPath("$.sourceAccountId").value(1))
                .andExpect(jsonPath("$.destinationAccountId").value(2))
                .andExpect(jsonPath("$.sourceNewBalance").value(47125.00))
                .andExpect(jsonPath("$.destinationNewBalance").value(27875.00));
    }

    @Test
    @DisplayName("L5: POST /internal/v1/ledger/holds/{holdId}/release returns 200 OK")
    void shouldReturn200OnReleaseHold() throws Exception {
        ReleaseHoldRequestDto requestDto = ReleaseHoldRequestDto.builder()
                .reason("CAPTURE_FAILED")
                .build();

        ReleaseHoldResponseDto responseDto = ReleaseHoldResponseDto.builder()
                .holdId(9001L)
                .status("CANCELLED")
                .availableBalance(new BigDecimal("49625.00"))
                .build();

        when(holdService.releaseHold(eq(9001L), any(ReleaseHoldRequestDto.class), any())).thenReturn(responseDto);

        mockMvc.perform(post("/internal/v1/ledger/holds/9001/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idemp-rel-001")
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdId").value(9001))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.availableBalance").value(49625.00));
    }

    @Test
    @DisplayName("L6: GET /internal/v1/ledger/holds/{holdId} returns 200 OK")
    void shouldReturn200OnGetHold() throws Exception {
        HoldDetailDto detailDto = HoldDetailDto.builder()
                .holdId(9001L)
                .referenceNo("TX-20261003-8F3A21C0")
                .accountId(1L)
                .amount(new BigDecimal("2500.00"))
                .status("RESERVED")
                .availableBalance(new BigDecimal("47125.00"))
                .ledgerBalance(new BigDecimal("49625.00"))
                .createdAt("2026-10-03T11:40:00.200Z")
                .build();

        when(holdService.getHold(9001L)).thenReturn(detailDto);

        mockMvc.perform(get("/internal/v1/ledger/holds/9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdId").value(9001))
                .andExpect(jsonPath("$.referenceNo").value("TX-20261003-8F3A21C0"))
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    @DisplayName("L6: GET /internal/v1/ledger/holds/{holdId} returns 404 when hold not found")
    void shouldReturn404WhenHoldNotFound() throws Exception {
        when(holdService.getHold(9999L)).thenThrow(new HoldNotFoundException(9999L));

        mockMvc.perform(get("/internal/v1/ledger/holds/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("HOLD_NOT_FOUND"));
    }

    @Test
    @DisplayName("L3: POST /internal/v1/ledger/holds returns 400 Bad Request on validation failure")
    void shouldReturn400OnInvalidHoldRequest() throws Exception {
        PlaceHoldRequestDto invalidRequest = PlaceHoldRequestDto.builder()
                .referenceNo("") // blank
                .accountId(null) // null
                .amount(new BigDecimal("-100.00")) // negative
                .build();

        mockMvc.perform(post("/internal/v1/ledger/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_PARAMETERS"));
    }
}
