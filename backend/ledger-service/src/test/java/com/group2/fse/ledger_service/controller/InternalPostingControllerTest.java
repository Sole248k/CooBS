package com.group2.fse.ledger_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.ledger_service.dto.TransferRequestDto;
import com.group2.fse.ledger_service.dto.TransferResponseDto;
import com.group2.fse.ledger_service.service.AccountBalanceService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalPostingController: L8 Atomic multi-posting endpoint (FIN-407)")
class InternalPostingControllerTest {

    @Mock
    private AccountBalanceService accountBalanceService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        InternalPostingController controller = new InternalPostingController(accountBalanceService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("executePostings successfully posts single interbank credit and returns 200 OK")
    void executePostingsSingleCreditSuccess() throws Exception {
        InternalPostingController.PostingItem item = InternalPostingController.PostingItem.builder()
                .referenceNo("IB-20261008-000045")
                .debitAccountId(9005L)
                .creditAccountId(4L)
                .amount(new BigDecimal("150.00"))
                .remarks("Inter-bank credit from BANKB")
                .build();

        InternalPostingController.PostingsBatchRequest request = InternalPostingController.PostingsBatchRequest.builder()
                .transactionId(UUID.randomUUID())
                .postings(List.of(item))
                .build();

        when(accountBalanceService.executeTransfer(any(TransferRequestDto.class), eq(0L), eq("127.0.0.1")))
                .thenReturn(TransferResponseDto.builder()
                        .transferReference("TRF-998877")
                        .status("COMPLETED")
                        .build());

        mockMvc.perform(post("/internal/v1/ledger/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posted").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.results[0].referenceNo").value("IB-20261008-000045"))
                .andExpect(jsonPath("$.results[0].status").value("POSTED"));
    }
}
