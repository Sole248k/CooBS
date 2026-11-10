package com.group2.fse.staff_bff.controller;

import com.group2.fse.staff_bff.dto.StaffAdminOverviewResponse;
import com.group2.fse.staff_bff.dto.StaffCustomer360Response;
import com.group2.fse.staff_bff.dto.StaffQueuesSummaryResponse;
import com.group2.fse.staff_bff.service.StaffBffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffBffController {

    private final StaffBffService staffBffService;

    // =========================================================================
    // WEB-03: Customer 360 Aggregation
    // =========================================================================
    @GetMapping("/customers/{customerId}/360")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<StaffCustomer360Response> getCustomer360(
            @PathVariable Long customerId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Staff request for Customer 360 on ID: {}", customerId);
        StaffCustomer360Response response = staffBffService.getCustomer360(customerId, correlationId, authorization);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // WEB-04: Counter Operations
    // =========================================================================
    @PostMapping("/counter/deposit")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> depositCash(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Staff counter cash deposit for account: {}", payload.get("accountId"));
        Map<String, Object> result = staffBffService.executeCashDeposit(payload, idempotencyKey, correlationId, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/counter/withdraw")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> withdrawCash(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Staff counter cash withdrawal from account: {}", payload.get("accountId"));
        Map<String, Object> result = staffBffService.executeCashWithdrawal(payload, idempotencyKey, correlationId, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/counter/transfer")
    @PreAuthorize("hasRole('TELLER')")
    public ResponseEntity<Map<String, Object>> assistedTransfer(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Teller assisted transfer from account: {}", payload.get("sourceAccountId"));
        Map<String, Object> result = staffBffService.executeAssistedTransfer(payload, idempotencyKey, correlationId, authorization);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    // =========================================================================
    // WEB-05: Approval Queues
    // =========================================================================
    @GetMapping("/queues/summary")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<StaffQueuesSummaryResponse> getQueuesSummary(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        StaffQueuesSummaryResponse response = staffBffService.getQueuesSummary(correlationId, authorization);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/queues/kyc/{requestId}/approve")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> approveKyc(
            @PathVariable Long requestId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Staff approval for KYC request #{}", requestId);
        Map<String, Object> result = staffBffService.approveKycUpdate(requestId, correlationId, authorization);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/queues/kyc/{requestId}/reject")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> rejectKyc(
            @PathVariable Long requestId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String reason = body != null ? body.get("reason") : "Insufficient proof";
        log.info("Staff rejection for KYC request #{}", requestId);
        Map<String, Object> result = staffBffService.rejectKycUpdate(requestId, reason, correlationId, authorization);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/queues/closures/{closureId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> approveClosure(
            @PathVariable Long closureId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Admin approval for account closure #{}", closureId);
        Map<String, Object> result = staffBffService.approveClosure(closureId, correlationId, authorization);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/queues/closures/{closureId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> rejectClosure(
            @PathVariable Long closureId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String reason = body != null ? body.get("reason") : "Closure criteria not met";
        log.info("Admin rejection for account closure #{}", closureId);
        Map<String, Object> result = staffBffService.rejectClosure(closureId, reason, correlationId, authorization);
        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // WEB-06: Accounts Lifecycle & Governance
    // =========================================================================
    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllAccounts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        List<Map<String, Object>> accounts = staffBffService.getAllAccounts(correlationId, authorization);
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> openAccount(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Staff opening account for customer: {}", payload.get("customerId"));
        Map<String, Object> created = staffBffService.openAccount(payload, correlationId, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @RequestMapping(value = "/accounts/{accountId}/status", method = {RequestMethod.PATCH, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> updateAccountStatus(
            @PathVariable Long accountId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String status = body.get("status");
        log.info("Staff setting status of account {} to {}", accountId, status);
        Map<String, Object> result = staffBffService.updateAccountStatus(accountId, status, correlationId, authorization);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/accounts/{accountId}/closure-request")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> requestAccountClosure(
            @PathVariable Long accountId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String reason = body != null ? body.get("reason") : "Branch counter closure request";
        log.info("Staff requesting closure for account {}: {}", accountId, reason);
        Map<String, Object> result = staffBffService.requestAccountClosure(accountId, reason, correlationId, authorization);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping("/accounts/{accountId}/flags")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> addAccountFlag(
            @PathVariable Long accountId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String reason = body.get("reason");
        log.info("Staff adding flag to account {}: {}", accountId, reason);
        Map<String, Object> result = staffBffService.addAccountFlag(accountId, reason, correlationId, authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/accounts/{accountId}/flags/{flagId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> removeAccountFlag(
            @PathVariable Long accountId,
            @PathVariable Long flagId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Admin removing flag {} from account {}", flagId, accountId);
        Map<String, Object> result = staffBffService.removeAccountFlag(accountId, flagId, correlationId, authorization);
        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // WEB-07: Tracker & Reversals
    // =========================================================================
    @GetMapping("/transactions/{transactionId}/timeline")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getTimeline(
            @PathVariable String transactionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        Map<String, Object> timeline = staffBffService.getTransactionTimeline(transactionId, correlationId, authorization);
        return ResponseEntity.ok(timeline);
    }

    @PostMapping("/transactions/{transactionId}/reversal")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> requestReversal(
            @PathVariable String transactionId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String reason = body != null ? body.get("reason") : "Branch counter teller reversal";
        log.info("Staff requesting reversal for transaction {}: {}", transactionId, reason);
        Map<String, Object> response = staffBffService.requestReversal(transactionId, reason, idempotencyKey, correlationId, authorization);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // =========================================================================
    // WEB-08: Audit Statement & Seals
    // =========================================================================
    @GetMapping("/audit/accounts/{accountId}/statement")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getAccountStatement(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        Map<String, Object> statement = staffBffService.getAccountStatement(accountId, page, size, correlationId, authorization);
        return ResponseEntity.ok(statement);
    }

    @GetMapping("/audit/verify-chain/{accountId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> verifyChain(
            @PathVariable Long accountId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        Map<String, Object> verification = staffBffService.verifyAccountChain(accountId, correlationId, authorization);
        return ResponseEntity.ok(verification);
    }

    @GetMapping("/audit/seals/{businessDate}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getDailySeal(
            @PathVariable String businessDate,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        Map<String, Object> seal = staffBffService.getDailySeal(businessDate, correlationId, authorization);
        return ResponseEntity.ok(seal);
    }

    // =========================================================================
    // WEB-09 & WEB-10: Admin Batch & Dead Letter Queues
    // =========================================================================
    @GetMapping("/admin/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffAdminOverviewResponse> getAdminOverview(
            @RequestParam(required = false) String businessDate,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        StaffAdminOverviewResponse overview = staffBffService.getAdminOverview(businessDate, correlationId, authorization);
        return ResponseEntity.ok(overview);
    }

    @PostMapping("/admin/batch/eod/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerEod(
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Admin triggering EOD batch execution");
        Map<String, Object> result = staffBffService.triggerEodBatch(payload, correlationId, authorization);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/batch/eod/runs")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllBatchRuns(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        List<Map<String, Object>> runs = staffBffService.getAllBatchRuns(correlationId, authorization);
        return ResponseEntity.ok(runs);
    }

    @GetMapping("/batch/eod/runs/{batchRunId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getBatchRun(
            @PathVariable Long batchRunId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        Map<String, Object> run = staffBffService.getBatchRun(batchRunId, correlationId, authorization);
        return ResponseEntity.ok(run);
    }

    @GetMapping("/admin/dlq")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getDlqMessages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        List<Map<String, Object>> messages = staffBffService.getDlqMessages(correlationId, authorization);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/admin/dlq/{recordId}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> replayDlqRecord(
            @PathVariable String recordId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Admin replaying DLQ message ID: {}", recordId);
        Map<String, Object> result = staffBffService.replayDlqRecord(recordId, correlationId, authorization);
        return ResponseEntity.ok(result);
    }
}
