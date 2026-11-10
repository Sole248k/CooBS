package com.group2.fse.transaction_service.controller;

import com.group2.fse.transaction_service.dto.TimelineResponseDto;
import com.group2.fse.transaction_service.dto.TimelineStageDto;
import com.group2.fse.transaction_service.dto.TransactionDetailResponseDto;
import com.group2.fse.transaction_service.exception.GlobalExceptionHandler;
import com.group2.fse.transaction_service.exception.TransactionNotFoundException;
import com.group2.fse.transaction_service.service.TimelineService;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimelineController & TransactionQueryController Unit Tests (FIN-304)")
class TimelineControllerTest {

    @Mock
    private TimelineService timelineService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TimelineController timelineController = new TimelineController(timelineService);
        TransactionQueryController queryController = new TransactionQueryController(timelineService);
        mockMvc = MockMvcBuilders.standaloneSetup(timelineController, queryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{id}/timeline returns 3-stage customer view")
    void testGetTimelineCustomerView() throws Exception {
        UUID txnId = UUID.randomUUID();
        TimelineResponseDto responseDto = TimelineResponseDto.builder()
                .transactionId(txnId)
                .trackingReference("TX-20261006-A1B2C3D4")
                .status("PROCESSING")
                .overallStatus("PROCESSING")
                .view("CUSTOMER")
                .currentStage(2)
                .totalStages(3)
                .stages(List.of(
                        TimelineStageDto.builder()
                                .stageNumber(1)
                                .stageKey("SUBMITTED")
                                .stageName("INITIATED")
                                .title("Transfer Submitted")
                                .subtitle("Your transfer request has been received and verified.")
                                .status("COMPLETED")
                                .outcome("PASSED")
                                .occurredAt(Instant.now())
                                .timestamp(Instant.now())
                                .build(),
                        TimelineStageDto.builder()
                                .stageNumber(2)
                                .stageKey("IN_REVIEW")
                                .stageName("PROCESSING")
                                .title("Security & Clearing")
                                .subtitle("Automated security screening and cooperative network clearing.")
                                .status("IN_PROGRESS")
                                .outcome("PENDING")
                                .build(),
                        TimelineStageDto.builder()
                                .stageNumber(3)
                                .stageKey("COMPLETED")
                                .stageName("POSTED")
                                .title("Transfer Completed")
                                .subtitle("Funds have been successfully deposited into the recipient's account.")
                                .status("PENDING")
                                .outcome("PENDING")
                                .build()
                ))
                .build();

        when(timelineService.getTimeline(eq(txnId.toString()), any(), any())).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/transactions/{transactionId}/timeline", txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txnId.toString()))
                .andExpect(jsonPath("$.trackingReference").value("TX-20261006-A1B2C3D4"))
                .andExpect(jsonPath("$.view").value("CUSTOMER"))
                .andExpect(jsonPath("$.totalStages").value(3))
                .andExpect(jsonPath("$.currentStage").value(2))
                .andExpect(jsonPath("$.stages", hasSize(3)))
                .andExpect(jsonPath("$.stages[0].stageKey").value("SUBMITTED"))
                .andExpect(jsonPath("$.stages[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.stages[1].stageKey").value("IN_REVIEW"))
                .andExpect(jsonPath("$.stages[1].status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{id}/timeline?view=STAFF returns 11-stage staff view")
    void testGetTimelineStaffView() throws Exception {
        UUID txnId = UUID.randomUUID();
        TimelineResponseDto responseDto = TimelineResponseDto.builder()
                .transactionId(txnId)
                .trackingReference("TX-20261006-A1B2C3D4")
                .status("POSTED")
                .overallStatus("COMPLETED")
                .view("STAFF")
                .currentStage(11)
                .totalStages(11)
                .stages(List.of(
                        TimelineStageDto.builder()
                                .stageNo(1)
                                .stageNumber(1)
                                .stageKey("INITIATED")
                                .stageName("INITIATED")
                                .title("Initiated")
                                .service("transaction-service")
                                .status("COMPLETED")
                                .outcome("PASSED")
                                .occurredAt(Instant.now())
                                .timestamp(Instant.now())
                                .build(),
                        TimelineStageDto.builder()
                                .stageNo(11)
                                .stageNumber(11)
                                .stageKey("RECONCILIATION")
                                .stageName("RECONCILIATION")
                                .title("Reconciliation")
                                .service("orchestration-service")
                                .status("COMPLETED")
                                .outcome("PASSED")
                                .occurredAt(Instant.now())
                                .timestamp(Instant.now())
                                .build()
                ))
                .build();

        when(timelineService.getTimeline(eq(txnId.toString()), eq("STAFF"), any())).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/transactions/{transactionId}/timeline?view=STAFF", txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view").value("STAFF"))
                .andExpect(jsonPath("$.totalStages").value(11))
                .andExpect(jsonPath("$.overallStatus").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{id} returns full transaction detail (T2)")
    void testGetTransactionDetail() throws Exception {
        UUID txnId = UUID.randomUUID();
        TransactionDetailResponseDto detailDto = TransactionDetailResponseDto.builder()
                .transactionId(txnId)
                .referenceNo("TX-20261006-A1B2C3D4")
                .channel("APP")
                .sourceAccountId(1L)
                .sourceAccountNumber("ACC_10000001")
                .destinationAccountId(2L)
                .destinationAccountNumber("ACC_10000002")
                .destinationBankCode("COOBS")
                .amount(new BigDecimal("2500.00"))
                .currency("PHP")
                .category("TRANSFER")
                .remarks("Lunch money")
                .status("POSTED")
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();

        when(timelineService.getTransactionDetail(eq(txnId.toString()))).thenReturn(detailDto);

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", txnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txnId.toString()))
                .andExpect(jsonPath("$.referenceNo").value("TX-20261006-A1B2C3D4"))
                .andExpect(jsonPath("$.sourceAccountId").value(1))
                .andExpect(jsonPath("$.destinationAccountNumber").value("ACC_10000002"))
                .andExpect(jsonPath("$.amount").value(2500.00))
                .andExpect(jsonPath("$.status").value("POSTED"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{id} with unknown ID returns 404 NOT_FOUND")
    void testGetUnknownTransactionReturns404() throws Exception {
        when(timelineService.getTransactionDetail(eq("unknown-id")))
                .thenThrow(new TransactionNotFoundException("Transaction not found for identifier: unknown-id"));

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", "unknown-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSACTION_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{trackingRef}/status returns Ingress -> Screened -> Committed -> Notified execution states")
    void testGetExecutionStatus() throws Exception {
        UUID txnId = UUID.randomUUID();
        String trackingRef = "TX-20261006-A1B2C3D4";

        com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto statusDto =
                com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto.builder()
                        .transactionId(txnId)
                        .trackingReference(trackingRef)
                        .status("PROCESSING")
                        .overallStatus("PROCESSING")
                        .currentExecutionState("Ingress")
                        .executionFlow("Ingress -> Screened -> Committed -> Notified")
                        .executionStates(List.of(
                                com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto.ExecutionStateDto.builder()
                                        .name("Ingress")
                                        .stateKey("INGRESS")
                                        .status("COMPLETED")
                                        .outcome("PASSED")
                                        .build(),
                                com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto.ExecutionStateDto.builder()
                                        .name("Screened")
                                        .stateKey("SCREENED")
                                        .status("IN_PROGRESS")
                                        .outcome("PENDING")
                                        .build(),
                                com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto.ExecutionStateDto.builder()
                                        .name("Committed")
                                        .stateKey("COMMITTED")
                                        .status("PENDING")
                                        .outcome("PENDING")
                                        .build(),
                                com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto.ExecutionStateDto.builder()
                                        .name("Notified")
                                        .stateKey("NOTIFIED")
                                        .status("PENDING")
                                        .outcome("PENDING")
                                        .build()
                        ))
                        .build();

        when(timelineService.getExecutionStatus(eq(trackingRef))).thenReturn(statusDto);

        mockMvc.perform(get("/api/v1/transactions/{trackingRef}/status", trackingRef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingReference").value(trackingRef))
                .andExpect(jsonPath("$.currentExecutionState").value("Ingress"))
                .andExpect(jsonPath("$.executionFlow").value("Ingress -> Screened -> Committed -> Notified"))
                .andExpect(jsonPath("$.executionStates", hasSize(4)))
                .andExpect(jsonPath("$.executionStates[0].name").value("Ingress"))
                .andExpect(jsonPath("$.executionStates[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.executionStates[1].name").value("Screened"))
                .andExpect(jsonPath("$.executionStates[2].name").value("Committed"))
                .andExpect(jsonPath("$.executionStates[3].name").value("Notified"));
    }
}
