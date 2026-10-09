package com.group2.fse.ledger_service.controller;

import com.group2.fse.ledger_service.dto.PostingsRequestDto;
import com.group2.fse.ledger_service.dto.PostingsResponseDto;
import com.group2.fse.ledger_service.exception.GlobalExceptionHandler;
import com.group2.fse.ledger_service.service.PostingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalPostingController: L8 Atomic multi-posting endpoint (FIN-407)")
class InternalPostingControllerTest {

    private static final String INTERBANK_CREDIT = """
            {
              "transactionId": "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11",
              "postings": [
                { "referenceNo": "IB-20261008-000045", "debitAccountId": 9005, "creditAccountId": 4,
                  "amount": "150.00", "remarks": "Inter-bank credit from BANKB" }
              ]
            }
            """;

    @Mock
    private PostingsService postingsService;

    @InjectMocks
    private InternalPostingController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static PostingsResponseDto answer(int posted, int failed, String total, String errorCode) {
        return PostingsResponseDto.builder()
                .posted(posted).failed(failed).totalAmount(new BigDecimal(total))
                .results(List.of(new PostingsResponseDto.PostingResult("IB-20261008-000045",
                        errorCode == null ? "POSTED" : "FAILED", errorCode)))
                .build();
    }

    @Test
    @DisplayName("An inter-bank credit answers 200 with money as a string, and the amount is read from a string")
    void interbankCreditIsPosted() throws Exception {
        when(postingsService.post(any(PostingsRequestDto.class), any())).thenReturn(answer(1, 0, "150.00", null));

        mockMvc.perform(post("/internal/v1/ledger/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11:posting")
                        .content(INTERBANK_CREDIT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posted").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.totalAmount").value("150.00"))
                .andExpect(jsonPath("$.results[0].referenceNo").value("IB-20261008-000045"))
                .andExpect(jsonPath("$.results[0].status").value("POSTED"))
                .andExpect(jsonPath("$.results[0].errorCode").value(nullValue()));

        ArgumentCaptor<PostingsRequestDto> sent = ArgumentCaptor.forClass(PostingsRequestDto.class);
        verify(postingsService).post(sent.capture(), any());
        assertThat(sent.getValue().getPostings().get(0).getAmount()).isEqualByComparingTo("150.00");
        assertThat(sent.getValue().getPostings().get(0).getDebitAccountId()).isEqualTo(9005L);
    }

    @Test
    @DisplayName("A refused posting is still 200: the caller reads failed and the error code")
    void refusedPostingIsReportedInTheBody() throws Exception {
        when(postingsService.post(any(PostingsRequestDto.class), any())).thenReturn(answer(0, 1, "0.00", "ACCOUNT_NOT_ACTIVE"));

        mockMvc.perform(post("/internal/v1/ledger/postings").contentType(MediaType.APPLICATION_JSON).content(INTERBANK_CREDIT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posted").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.results[0].errorCode").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("A request with no postings, a bad amount or a reference too long for the journal is 400")
    void malformedRequestsAreRejected() throws Exception {
        String noPostings = "{ \"transactionId\": \"6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11\", \"postings\": [] }";
        String negativeAmount = INTERBANK_CREDIT.replace("\"150.00\"", "\"-1.00\"");
        String fiveDecimals = INTERBANK_CREDIT.replace("\"150.00\"", "\"1.00005\"");
        String longReference = INTERBANK_CREDIT.replace("IB-20261008-000045", "IB-20261008-000045-AAAAAAAAAAAAAAAA");

        for (String body : List.of(noPostings, negativeAmount, fiveDecimals, longReference)) {
            mockMvc.perform(post("/internal/v1/ledger/postings").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_PARAMETERS"));
        }
        verify(postingsService, never()).post(any(), any());
    }

    @Test
    @DisplayName("A request that names neither a batch step nor a transaction is 400")
    void theCallerMustSayWhatThePostingsBelongTo() throws Exception {
        String anonymous = INTERBANK_CREDIT.replace("\"transactionId\": \"6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11\",", "");

        mockMvc.perform(post("/internal/v1/ledger/postings").contentType(MediaType.APPLICATION_JSON).content(anonymous))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSACTION"));
        verify(postingsService, never()).post(any(), any());
    }

    @Test
    @DisplayName("A batch step is accepted with batchRunId and stepName, and they come back in the answer")
    void batchStepIsAccepted() throws Exception {
        String batch = """
                {
                  "batchRunId": 31,
                  "stepName": "INTEREST_ACCRUAL",
                  "postings": [
                    { "referenceNo": "EOD-20261003-INT-000001", "debitAccountId": 9002, "creditAccountId": 1,
                      "amount": "4.08", "remarks": "Daily savings interest" }
                  ]
                }
                """;
        when(postingsService.post(any(PostingsRequestDto.class), any())).thenReturn(PostingsResponseDto.builder()
                .batchRunId(31L).stepName("INTEREST_ACCRUAL").posted(1).failed(0).totalAmount(new BigDecimal("4.08"))
                .results(List.of(new PostingsResponseDto.PostingResult("EOD-20261003-INT-000001", "POSTED", null)))
                .build());

        mockMvc.perform(post("/internal/v1/ledger/postings").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchRunId").value(31))
                .andExpect(jsonPath("$.stepName").value("INTEREST_ACCRUAL"))
                .andExpect(jsonPath("$.totalAmount").value("4.08"))
                .andExpect(jsonPath("$.transactionId").doesNotExist());
    }
}
