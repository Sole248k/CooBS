package com.group2.fse.transaction_service.service.impl;

import com.group2.fse.transaction_service.dto.ExecutionStatusResponseDto;
import com.group2.fse.transaction_service.dto.TimelineResponseDto;
import com.group2.fse.transaction_service.dto.TimelineStageDto;
import com.group2.fse.transaction_service.dto.TransactionDetailResponseDto;
import com.group2.fse.transaction_service.entity.TxnStageEvent;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.exception.TransactionNotFoundException;
import com.group2.fse.transaction_service.repository.TxnStageEventRepository;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import com.group2.fse.transaction_service.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineServiceImpl implements TimelineService {

    private final TxnTransactionRepository transactionRepository;
    private final TxnStageEventRepository stageEventRepository;

    private static final List<StaffStageDef> STAFF_STAGE_DEFINITIONS = List.of(
            new StaffStageDef(1, "INGRESS_ACCEPTED", "Ingress Accepted", "transaction-service"),
            new StaffStageDef(2, "PREFLIGHT_VALIDATED", "Pre-flight Solvency Checked", "transaction-service"),
            new StaffStageDef(3, "DISPATCHED_TO_ORCHESTRATOR", "Dispatched to Orchestrator", "transaction-service"),
            new StaffStageDef(4, "SAGA_INITIALIZED", "Saga State Machine Initialized", "orchestration-service"),
            new StaffStageDef(5, "RISK_EVALUATION_REQUESTED", "Risk Evaluation Requested", "orchestration-service"),
            new StaffStageDef(6, "RISK_EVALUATION_COMPLETED", "Risk Screening Passed", "orchestration-service"),
            new StaffStageDef(7, "LEDGER_HOLD_RESERVED", "Ledger Balance Hold Reserved", "orchestration-service"),
            new StaffStageDef(8, "LEDGER_POSTED", "Double-Entry SoR Committed", "orchestration-service"),
            new StaffStageDef(9, "EVENT_EMITTED", "Domain Event Emitted", "orchestration-service"),
            new StaffStageDef(10, "NOTIFICATIONS_DISPATCHED", "Member Notifications Sent", "orchestration-service"),
            new StaffStageDef(11, "COMPLETED", "Saga Fully Reconciled", "orchestration-service")
    );

    @Override
    @Transactional(readOnly = true)
    public ExecutionStatusResponseDto getExecutionStatus(String identifier) {
        TxnTransaction txn = resolveTransaction(identifier);
        List<TxnStageEvent> events = stageEventRepository.findByTransactionIdOrderByStageNoAsc(txn.getTransactionId());
        Map<Integer, TxnStageEvent> eventByStage = events.stream()
                .collect(Collectors.toMap(TxnStageEvent::getStageNo, e -> e, (e1, e2) -> e1));

        boolean isFailed = "FAILED".equalsIgnoreCase(txn.getStatus());

        List<ExecutionStatusResponseDto.ExecutionStateDto> states = new ArrayList<>();

        // 1. Ingress: Stages 1, 2, 3
        states.add(buildExecutionState("Ingress", "INGRESS",
                "Ingress Intake, Solvency Validation & Orchestrator Dispatch",
                List.of(1, 2, 3), eventByStage, isFailed, 0));

        // 2. Screened: Stages 4, 5, 6
        states.add(buildExecutionState("Screened", "SCREENED",
                "Saga Initialization & Multi-Tier Fraud/Risk Evaluation",
                List.of(4, 5, 6), eventByStage, isFailed, 3));

        // 3. Committed: Stages 7, 8
        states.add(buildExecutionState("Committed", "COMMITTED",
                "Ledger Balance Hold Reservation & Double-Entry SoR Mutation",
                List.of(7, 8), eventByStage, isFailed, 6));

        // 4. Notified: Stages 9, 10, 11
        states.add(buildExecutionState("Notified", "NOTIFIED",
                "Event Streaming, Customer Notifications & Saga Reconciliation",
                List.of(9, 10, 11), eventByStage, isFailed, 8));

        String currentState = "Ingress";
        for (ExecutionStatusResponseDto.ExecutionStateDto st : states) {
            if ("FAILED".equalsIgnoreCase(st.getStatus())) {
                currentState = st.getName();
                break;
            }
            if ("IN_PROGRESS".equalsIgnoreCase(st.getStatus())) {
                currentState = st.getName();
                break;
            }
            if ("COMPLETED".equalsIgnoreCase(st.getStatus())) {
                currentState = st.getName();
            }
        }

        String overallStatus = isFailed ? "FAILED" :
                (states.get(3).getStatus().equals("COMPLETED") || "POSTED".equalsIgnoreCase(txn.getStatus()) ? "COMPLETED" : "PROCESSING");

        return ExecutionStatusResponseDto.builder()
                .transactionId(txn.getTransactionId())
                .trackingReference(txn.getReferenceNo())
                .status(txn.getStatus())
                .overallStatus(overallStatus)
                .currentExecutionState(currentState)
                .executionFlow("Ingress -> Screened -> Committed -> Notified")
                .executionStates(states)
                .failureMessage(isFailed ? txn.getFailureCode() : null)
                .timelineUrl("/api/v1/transactions/" + txn.getTransactionId() + "/timeline")
                .build();
    }

    private ExecutionStatusResponseDto.ExecutionStateDto buildExecutionState(
            String name, String key, String desc, List<Integer> stageNos,
            Map<Integer, TxnStageEvent> events, boolean txnFailed, int prevLastStage) {

        int completedCount = 0;
        boolean hasFail = false;
        Instant firstTime = null;
        Instant lastTime = null;

        for (int s : stageNos) {
            TxnStageEvent evt = events.get(s);
            if (evt != null) {
                if (firstTime == null) {
                    firstTime = evt.getOccurredAt();
                }
                lastTime = evt.getOccurredAt();
                if ("FAILED".equalsIgnoreCase(evt.getOutcome())) {
                    hasFail = true;
                } else {
                    completedCount++;
                }
            }
        }

        String status;
        String outcome;
        if (hasFail) {
            status = "FAILED";
            outcome = "FAILED";
        } else if (completedCount == stageNos.size()) {
            status = "COMPLETED";
            outcome = "PASSED";
        } else if (completedCount > 0 || (prevLastStage > 0 && events.containsKey(prevLastStage) && !txnFailed)) {
            status = "IN_PROGRESS";
            outcome = "PENDING";
        } else {
            status = "PENDING";
            outcome = "PENDING";
        }

        Long durationMs = null;
        if (firstTime != null && lastTime != null) {
            durationMs = Math.max(0, Duration.between(firstTime, lastTime).toMillis());
        }

        return ExecutionStatusResponseDto.ExecutionStateDto.builder()
                .name(name)
                .stateKey(key)
                .description(desc)
                .stages(stageNos)
                .status(status)
                .outcome(outcome)
                .occurredAt(firstTime)
                .completedAt(status.equals("COMPLETED") ? lastTime : null)
                .durationMs(durationMs)
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public TimelineResponseDto getTimeline(String identifier, String requestedView, Authentication authentication) {
        TxnTransaction txn = resolveTransaction(identifier);
        List<TxnStageEvent> events = stageEventRepository.findByTransactionIdOrderByStageNoAsc(txn.getTransactionId());
        Map<Integer, TxnStageEvent> eventByStage = events.stream()
                .collect(Collectors.toMap(TxnStageEvent::getStageNo, e -> e, (e1, e2) -> e1));

        boolean isStaffView = determineIsStaffView(requestedView, authentication);
        if (isStaffView) {
            return buildStaffTimeline(txn, eventByStage);
        } else {
            return buildCustomerTimeline(txn, eventByStage);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionDetailResponseDto getTransactionDetail(String identifier) {
        TxnTransaction txn = resolveTransaction(identifier);
        return TransactionDetailResponseDto.builder()
                .transactionId(txn.getTransactionId())
                .referenceNo(txn.getReferenceNo())
                .channel(txn.getChannel())
                .sourceAccountId(txn.getSourceAccountId())
                .sourceAccountNumber(txn.getSourceAccountId() != null ? "ACC_" + (10000000 + txn.getSourceAccountId()) : null)
                .destinationAccountId(txn.getDestinationAccountId())
                .destinationAccountNumber(txn.getDestinationAccountNumber())
                .destinationBankCode(txn.getDestinationBankCode())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .category(txn.getCategory())
                .remarks(txn.getRemarks())
                .status(txn.getStatus())
                .failureCode(txn.getFailureCode())
                .failureReason(txn.getFailureCode())
                .reversedByTransactionId(txn.getReversesTransactionId())
                .createdAt(txn.getCreatedAt())
                .completedAt(txn.getCompletedAt())
                .build();
    }

    @Override
    @Transactional
    public void recordStage(UUID transactionId, int stageNo, String stageName, String outcome,
                            String detail, Instant occurredAt, String transactionStatus, String failureCode) {
        log.info("Recording stage for txn={}: stageNo={}, stageName={}, outcome={}, status={}",
                transactionId, stageNo, stageName, outcome, transactionStatus);

        Optional<TxnStageEvent> existing = stageEventRepository.findByTransactionIdAndStageNo(transactionId, stageNo);
        TxnStageEvent stageEvent = existing.orElseGet(() -> TxnStageEvent.builder()
                .transactionId(transactionId)
                .stageNo(stageNo)
                .build());

        stageEvent.setStageName(stageName);
        stageEvent.setOutcome(outcome != null ? outcome : "PASSED");
        stageEvent.setDetail(detail);
        stageEvent.setOccurredAt(occurredAt != null ? occurredAt : Instant.now());
        stageEventRepository.save(stageEvent);

        transactionRepository.findByTransactionId(transactionId).ifPresent(txn -> {
            if (transactionStatus != null && !transactionStatus.isBlank()) {
                txn.setStatus(transactionStatus);
            }
            if (failureCode != null && !failureCode.isBlank()) {
                txn.setFailureCode(failureCode);
            }
            if (stageNo == 11 || "POSTED".equalsIgnoreCase(transactionStatus) || "COMPLETED".equalsIgnoreCase(transactionStatus)) {
                if (txn.getCompletedAt() == null) {
                    txn.setCompletedAt(occurredAt != null ? occurredAt : Instant.now());
                }
            } else if ("FAILED".equalsIgnoreCase(transactionStatus)) {
                if (txn.getCompletedAt() == null) {
                    txn.setCompletedAt(occurredAt != null ? occurredAt : Instant.now());
                }
            }
            transactionRepository.save(txn);
        });
    }

    private TxnTransaction resolveTransaction(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new TransactionNotFoundException("Transaction identifier must not be empty.");
        }

        try {
            UUID uuid = UUID.fromString(identifier);
            Optional<TxnTransaction> byId = transactionRepository.findByTransactionId(uuid);
            if (byId.isPresent()) {
                return byId.get();
            }
        } catch (IllegalArgumentException ignored) {
            // Identifier is not a UUID, check by referenceNo
        }

        return transactionRepository.findByReferenceNo(identifier)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found for identifier: " + identifier));
    }

    private boolean determineIsStaffView(String requestedView, Authentication authentication) {
        if ("STAFF".equalsIgnoreCase(requestedView)) {
            return true;
        }
        if ("CUSTOMER".equalsIgnoreCase(requestedView)) {
            return false;
        }

        if (authentication != null && authentication.getAuthorities() != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String auth = authority.getAuthority();
                if (auth.contains("STAFF") || auth.contains("TELLER") || auth.contains("ADMIN") || auth.contains("SERVICE")) {
                    return true;
                }
            }
        }
        return false;
    }

    private TimelineResponseDto buildStaffTimeline(TxnTransaction txn, Map<Integer, TxnStageEvent> eventByStage) {
        List<TimelineStageDto> stageDtos = new ArrayList<>();
        int maxCompletedStage = 0;
        boolean hasFailure = "FAILED".equalsIgnoreCase(txn.getStatus());

        for (StaffStageDef def : STAFF_STAGE_DEFINITIONS) {
            TxnStageEvent evt = eventByStage.get(def.stageNo);
            if (evt != null) {
                boolean passed = !"FAILED".equalsIgnoreCase(evt.getOutcome());
                if (passed) {
                    maxCompletedStage = Math.max(maxCompletedStage, def.stageNo);
                } else {
                    hasFailure = true;
                }
                stageDtos.add(TimelineStageDto.builder()
                        .stageNo(def.stageNo)
                        .stageNumber(def.stageNo)
                        .stageKey(def.key)
                        .stageName(def.key)
                        .title(def.title)
                        .service(def.service)
                        .status(passed ? "COMPLETED" : "FAILED")
                        .outcome(evt.getOutcome())
                        .occurredAt(evt.getOccurredAt())
                        .timestamp(evt.getOccurredAt())
                        .detail(evt.getDetail())
                        .build());
            } else {
                String status;
                if (hasFailure) {
                    status = "PENDING";
                } else if (def.stageNo == maxCompletedStage + 1) {
                    status = "IN_PROGRESS";
                } else {
                    status = "PENDING";
                }

                stageDtos.add(TimelineStageDto.builder()
                        .stageNo(def.stageNo)
                        .stageNumber(def.stageNo)
                        .stageKey(def.key)
                        .stageName(def.key)
                        .title(def.title)
                        .service(def.service)
                        .status(status)
                        .outcome("PENDING")
                        .occurredAt(null)
                        .timestamp(null)
                        .detail(null)
                        .build());
            }
        }

        String overallStatus;
        if (hasFailure) {
            overallStatus = "FAILED";
        } else if (maxCompletedStage >= 11 || "POSTED".equalsIgnoreCase(txn.getStatus()) || "COMPLETED".equalsIgnoreCase(txn.getStatus())) {
            overallStatus = "COMPLETED";
            maxCompletedStage = 11;
        } else {
            overallStatus = "PROCESSING";
        }

        return TimelineResponseDto.builder()
                .transactionId(txn.getTransactionId())
                .trackingReference(txn.getReferenceNo())
                .status(hasFailure ? "FAILED" : (maxCompletedStage >= 11 ? "POSTED" : "PROCESSING"))
                .overallStatus(overallStatus)
                .view("STAFF")
                .currentStage(hasFailure ? Math.max(maxCompletedStage, 1) : Math.max(maxCompletedStage, 1))
                .totalStages(11)
                .stages(stageDtos)
                .failureMessage(hasFailure ? txn.getFailureCode() : null)
                .build();
    }

    private TimelineResponseDto buildCustomerTimeline(TxnTransaction txn, Map<Integer, TxnStageEvent> eventByStage) {
        boolean isFailed = "FAILED".equalsIgnoreCase(txn.getStatus());
        boolean isCompleted = "POSTED".equalsIgnoreCase(txn.getStatus()) || "COMPLETED".equalsIgnoreCase(txn.getStatus());

        int maxStage = eventByStage.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (isCompleted) {
            maxStage = 11;
        }

        Instant stage1Time = eventByStage.containsKey(1) ? eventByStage.get(1).getOccurredAt() : txn.getCreatedAt();
        Instant stage2Time = null;
        for (int i = 2; i <= 7; i++) {
            if (eventByStage.containsKey(i)) {
                stage2Time = eventByStage.get(i).getOccurredAt();
            }
        }
        Instant stage3Time = txn.getCompletedAt();
        if (stage3Time == null && eventByStage.containsKey(8)) {
            stage3Time = eventByStage.get(8).getOccurredAt();
        }

        List<TimelineStageDto> customerStages = new ArrayList<>();

        if (isFailed) {
            int failedAtCustomerStage = maxStage >= 8 ? 3 : (maxStage >= 2 ? 2 : 1);
            customerStages.add(createCustomerStage(1, "SUBMITTED", "INITIATED", "Transfer Submitted",
                    "Your transfer request has been received and verified.",
                    failedAtCustomerStage == 1 ? "FAILED" : "COMPLETED",
                    failedAtCustomerStage == 1 ? "FAILED" : "PASSED",
                    stage1Time));

            customerStages.add(createCustomerStage(2, "IN_REVIEW", "PROCESSING", "Security & Clearing",
                    "Automated security screening and cooperative network clearing.",
                    failedAtCustomerStage == 2 ? "FAILED" : (failedAtCustomerStage > 2 ? "COMPLETED" : "PENDING"),
                    failedAtCustomerStage == 2 ? "FAILED" : (failedAtCustomerStage > 2 ? "PASSED" : "PENDING"),
                    failedAtCustomerStage >= 2 ? stage2Time : null));

            customerStages.add(createCustomerStage(3, "COMPLETED", "POSTED", "Transfer Completed",
                    "Funds have been successfully deposited into the recipient's account.",
                    failedAtCustomerStage == 3 ? "FAILED" : "PENDING",
                    failedAtCustomerStage == 3 ? "FAILED" : "PENDING",
                    failedAtCustomerStage == 3 ? stage3Time : null));

            return TimelineResponseDto.builder()
                    .transactionId(txn.getTransactionId())
                    .trackingReference(txn.getReferenceNo())
                    .status("FAILED")
                    .overallStatus("FAILED")
                    .view("CUSTOMER")
                    .currentStage(failedAtCustomerStage)
                    .totalStages(3)
                    .stages(customerStages)
                    .failureMessage(txn.getFailureCode() != null ? txn.getFailureCode() : "Transaction processing failed.")
                    .build();
        }

        if (isCompleted || maxStage >= 8) {
            customerStages.add(createCustomerStage(1, "SUBMITTED", "INITIATED", "Transfer Submitted",
                    "Your transfer request has been received and verified.", "COMPLETED", "PASSED", stage1Time));
            customerStages.add(createCustomerStage(2, "IN_REVIEW", "PROCESSING", "Security & Clearing",
                    "Automated security screening and cooperative network clearing.", "COMPLETED", "PASSED", stage2Time != null ? stage2Time : stage1Time));
            customerStages.add(createCustomerStage(3, "COMPLETED", "POSTED", "Transfer Completed",
                    "Funds have been successfully deposited into the recipient's account.", "COMPLETED", "PASSED", stage3Time != null ? stage3Time : stage1Time));

            return TimelineResponseDto.builder()
                    .transactionId(txn.getTransactionId())
                    .trackingReference(txn.getReferenceNo())
                    .status("POSTED")
                    .overallStatus("COMPLETED")
                    .view("CUSTOMER")
                    .currentStage(3)
                    .totalStages(3)
                    .stages(customerStages)
                    .failureMessage(null)
                    .build();
        }

        // In progress
        int currentCustomerStage = maxStage >= 3 ? 2 : 1;
        customerStages.add(createCustomerStage(1, "SUBMITTED", "INITIATED", "Transfer Submitted",
                "Your transfer request has been received and verified.", "COMPLETED", "PASSED", stage1Time));

        customerStages.add(createCustomerStage(2, "IN_REVIEW", "PROCESSING", "Security & Clearing",
                "Automated security screening and cooperative network clearing.",
                "IN_PROGRESS", "PENDING", null));

        customerStages.add(createCustomerStage(3, "COMPLETED", "POSTED", "Transfer Completed",
                "Funds have been successfully deposited into the recipient's account.",
                "PENDING", "PENDING", null));

        return TimelineResponseDto.builder()
                .transactionId(txn.getTransactionId())
                .trackingReference(txn.getReferenceNo())
                .status("PROCESSING")
                .overallStatus("PROCESSING")
                .view("CUSTOMER")
                .currentStage(currentCustomerStage)
                .totalStages(3)
                .stages(customerStages)
                .failureMessage(null)
                .build();
    }

    private TimelineStageDto createCustomerStage(int stageNumber, String stageKey, String stageName,
                                                 String title, String subtitle, String status,
                                                 String outcome, Instant time) {
        return TimelineStageDto.builder()
                .stageNumber(stageNumber)
                .stageKey(stageKey)
                .stageName(stageName)
                .title(title)
                .subtitle(subtitle)
                .status(status)
                .outcome(outcome)
                .occurredAt(time)
                .timestamp(time)
                .build();
    }

    private record StaffStageDef(int stageNo, String key, String title, String service) {}
}
