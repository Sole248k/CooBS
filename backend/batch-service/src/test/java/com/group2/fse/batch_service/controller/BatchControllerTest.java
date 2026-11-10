package com.group2.fse.batch_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.group2.fse.batch_service.dto.BatchRunDetailsResponseDto;
import com.group2.fse.batch_service.dto.BatchStepResponseDto;
import com.group2.fse.batch_service.dto.EodTriggerRequestDto;
import com.group2.fse.batch_service.dto.EodTriggerResponseDto;
import com.group2.fse.batch_service.exception.BatchAlreadyRunningException;
import com.group2.fse.batch_service.exception.GlobalExceptionHandler;
import com.group2.fse.batch_service.exception.ResourceNotFoundException;
import com.group2.fse.batch_service.security.UserPrincipal;
import com.group2.fse.batch_service.service.BatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchController REST API Contract Tests")
class BatchControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private BatchService batchService;

    @InjectMocks
    private BatchController batchController;

    private Authentication adminAuth;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(batchController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserPrincipal adminPrincipal = UserPrincipal.create(3L, "admin_user", List.of("ROLE_ADMIN"));
        adminAuth = new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities());
    }

    @Test
    @DisplayName("B1: POST /api/v1/batch/eod/trigger returns 202 Accepted with trigger details")
    void triggerEod_Returns202() throws Exception {
        EodTriggerResponseDto responseDto = EodTriggerResponseDto.builder()
                .batchRunId(31L)
                .businessDate(LocalDate.of(2026, 10, 3))
                .status("RUNNING")
                .startedAt(Instant.parse("2026-10-03T17:00:00Z"))
                .build();

        when(batchService.triggerEod(any(), any())).thenReturn(responseDto);

        EodTriggerRequestDto request = EodTriggerRequestDto.builder()
                .businessDate(LocalDate.of(2026, 10, 3))
                .build();

        mockMvc.perform(post("/api/v1/batch/eod/trigger")
                        .principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.batchRunId").value(31))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.businessDate").value("2026-10-03"))
                .andExpect(jsonPath("$.startedAt").value("2026-10-03T17:00:00Z"));
    }

    @Test
    @DisplayName("B1: POST /api/v1/batch/eod/trigger returns 409 Conflict when batch is already running")
    void triggerEod_AlreadyRunning_Returns409() throws Exception {
        when(batchService.triggerEod(any(), any()))
                .thenThrow(new BatchAlreadyRunningException("A batch run is currently in progress"));

        mockMvc.perform(post("/api/v1/batch/eod/trigger")
                        .principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A batch run is currently in progress"));
    }

    @Test
    @DisplayName("B1: POST /api/v1/batch/eod/trigger returns 400 Bad Request when business date is in the future")
    void triggerEod_FutureDate_Returns400() throws Exception {
        EodTriggerRequestDto request = EodTriggerRequestDto.builder()
                .businessDate(LocalDate.now().plusDays(2))
                .build();

        mockMvc.perform(post("/api/v1/batch/eod/trigger")
                        .principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Business date cannot be in the future"));
    }

    @Test
    @DisplayName("B2: GET /api/v1/batch/eod/runs/{batchRunId} returns 200 OK with steps matching contract shape")
    void getBatchRun_Returns200() throws Exception {
        BatchStepResponseDto stepDto = BatchStepResponseDto.builder()
                .stepNo(1)
                .stepName("INTEREST_ACCRUAL")
                .status("COMPLETED")
                .itemsProcessed(3)
                .totalAmount("12.24")
                .durationMs(820)
                .message(null)
                .build();

        BatchRunDetailsResponseDto detailsDto = BatchRunDetailsResponseDto.builder()
                .batchRunId(31L)
                .businessDate(LocalDate.of(2026, 10, 3))
                .status("COMPLETED")
                .triggeredBy(3L)
                .startedAt(Instant.parse("2026-10-03T17:00:00Z"))
                .finishedAt(Instant.parse("2026-10-03T17:00:06Z"))
                .steps(List.of(stepDto))
                .build();

        when(batchService.getBatchRun(31L)).thenReturn(detailsDto);

        mockMvc.perform(get("/api/v1/batch/eod/runs/31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchRunId").value(31))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.triggeredBy").value(3))
                .andExpect(jsonPath("$.steps[0].stepName").value("INTEREST_ACCRUAL"))
                .andExpect(jsonPath("$.steps[0].itemsProcessed").value(3));
    }

    @Test
    @DisplayName("B2: GET /api/v1/batch/eod/runs/{batchRunId} returns 404 when batch run not found")
    void getBatchRun_NotFound_Returns404() throws Exception {
        when(batchService.getBatchRun(999L))
                .thenThrow(new ResourceNotFoundException("Batch run not found with ID: 999"));

        mockMvc.perform(get("/api/v1/batch/eod/runs/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Batch run not found with ID: 999"));
    }

    @Test
    @DisplayName("B3: GET /api/v1/batch/eod/runs returns 200 OK with list of all runs")
    void listBatchRuns_Returns200() throws Exception {
        BatchRunDetailsResponseDto detailsDto = BatchRunDetailsResponseDto.builder()
                .batchRunId(31L)
                .businessDate(LocalDate.of(2026, 10, 3))
                .status("COMPLETED")
                .triggeredBy(3L)
                .startedAt(Instant.parse("2026-10-03T17:00:00Z"))
                .finishedAt(Instant.parse("2026-10-03T17:00:06Z"))
                .steps(List.of())
                .build();

        when(batchService.listBatchRuns()).thenReturn(List.of(detailsDto));

        mockMvc.perform(get("/api/v1/batch/eod/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].batchRunId").value(31))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }
}
