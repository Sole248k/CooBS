package com.group2.fse.transaction_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group2.fse.transaction_service.dto.HoldRequestDto;
import com.group2.fse.transaction_service.dto.HoldResponseDto;
import com.group2.fse.transaction_service.dto.ReversalRequestDto;
import com.group2.fse.transaction_service.dto.ReversalResponseDto;
import com.group2.fse.transaction_service.service.HoldService;
import com.group2.fse.transaction_service.service.ReversalService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Hold and Reversal Controller Standalone Unit Tests")
class HoldAndReversalControllerTest {

    @Mock
    private HoldService holdService;

    @Mock
    private ReversalService reversalService;

    @InjectMocks
    private HoldController holdController;

    @InjectMocks
    private ReversalController reversalController;

    private MockMvc holdMockMvc;
    private MockMvc reversalMockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        holdMockMvc = MockMvcBuilders.standaloneSetup(holdController).build();
        reversalMockMvc = MockMvcBuilders.standaloneSetup(reversalController).build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /api/v1/holds creates hold successfully")
    void testPlaceHold() throws Exception {
        HoldRequestDto request = HoldRequestDto.builder()
                .accountId(10L)
                .amount(new BigDecimal("500.0000"))
                .reason("Security Verification Hold")
                .referenceNo("HLD-001")
                .build();

        HoldResponseDto response = HoldResponseDto.builder()
                .holdId("HLD-12345678")
                .accountId(10L)
                .amount(new BigDecimal("500.0000"))
                .reason("Security Verification Hold")
                .status("HELD")
                .createdAt(Instant.now())
                .build();

        when(holdService.placeHold(any(HoldRequestDto.class))).thenReturn(response);

        holdMockMvc.perform(post("/api/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId").value("HLD-12345678"))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.accountId").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/holds/{holdId}/release releases hold successfully")
    void testReleaseHold() throws Exception {
        HoldResponseDto response = HoldResponseDto.builder()
                .holdId("HLD-12345678")
                .status("RELEASED")
                .createdAt(Instant.now())
                .build();

        when(holdService.releaseHold("HLD-12345678")).thenReturn(response);

        holdMockMvc.perform(post("/api/v1/holds/HLD-12345678/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdId").value("HLD-12345678"))
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    @DisplayName("POST /api/v1/reversals executes reversal successfully")
    void testReverseTransaction() throws Exception {
        ReversalRequestDto request = ReversalRequestDto.builder()
                .originalReferenceNo("TRF-20260926-001")
                .amount(new BigDecimal("200.0000"))
                .reason("Customer Dispute")
                .build();

        ReversalResponseDto response = ReversalResponseDto.builder()
                .reversalId("REV-87654321")
                .originalReferenceNo("TRF-20260926-001")
                .amount(new BigDecimal("200.0000"))
                .status("REVERSED")
                .timestamp(Instant.now())
                .build();

        when(reversalService.executeReversal(any(ReversalRequestDto.class))).thenReturn(response);

        reversalMockMvc.perform(post("/api/v1/reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversalId").value("REV-87654321"))
                .andExpect(jsonPath("$.status").value("REVERSED"));
    }
}
