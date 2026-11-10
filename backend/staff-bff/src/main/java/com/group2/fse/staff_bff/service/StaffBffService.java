package com.group2.fse.staff_bff.service;

import com.group2.fse.staff_bff.dto.StaffAdminOverviewResponse;
import com.group2.fse.staff_bff.dto.StaffCustomer360Response;
import com.group2.fse.staff_bff.dto.StaffQueuesSummaryResponse;

import java.util.List;
import java.util.Map;

public interface StaffBffService {

    StaffCustomer360Response getCustomer360(Long customerId, String correlationId, String bearerToken);

    StaffQueuesSummaryResponse getQueuesSummary(String correlationId, String bearerToken);

    StaffAdminOverviewResponse getAdminOverview(String businessDate, String correlationId, String bearerToken);

    Map<String, Object> executeCashDeposit(Map<String, Object> payload, String idempotencyKey, String correlationId, String bearerToken);

    Map<String, Object> executeCashWithdrawal(Map<String, Object> payload, String idempotencyKey, String correlationId, String bearerToken);

    Map<String, Object> executeAssistedTransfer(Map<String, Object> payload, String idempotencyKey, String correlationId, String bearerToken);

    Map<String, Object> approveKycUpdate(Long requestId, String correlationId, String bearerToken);

    Map<String, Object> rejectKycUpdate(Long requestId, String reason, String correlationId, String bearerToken);

    Map<String, Object> approveClosure(Long closureId, String correlationId, String bearerToken);

    Map<String, Object> rejectClosure(Long closureId, String reason, String correlationId, String bearerToken);

    Map<String, Object> requestAccountClosure(Long accountId, String reason, String correlationId, String bearerToken);

    List<Map<String, Object>> getAllAccounts(String correlationId, String bearerToken);

    Map<String, Object> openAccount(Map<String, Object> payload, String correlationId, String bearerToken);

    Map<String, Object> updateAccountStatus(Long accountId, String status, String correlationId, String bearerToken);

    Map<String, Object> addAccountFlag(Long accountId, String reason, String correlationId, String bearerToken);

    Map<String, Object> removeAccountFlag(Long accountId, Long flagId, String correlationId, String bearerToken);

    Map<String, Object> getTransactionTimeline(String transactionId, String correlationId, String bearerToken);

    Map<String, Object> requestReversal(String transactionId, String reason, String idempotencyKey, String correlationId, String bearerToken);

    Map<String, Object> getAccountStatement(Long accountId, int page, int size, String correlationId, String bearerToken);

    Map<String, Object> verifyAccountChain(Long accountId, String correlationId, String bearerToken);

    Map<String, Object> getDailySeal(String businessDate, String correlationId, String bearerToken);

    Map<String, Object> triggerEodBatch(Map<String, Object> payload, String correlationId, String bearerToken);

    Map<String, Object> getBatchRun(Long batchRunId, String correlationId, String bearerToken);

    List<Map<String, Object>> getAllBatchRuns(String correlationId, String bearerToken);

    List<Map<String, Object>> getDlqMessages(String correlationId, String bearerToken);

    Map<String, Object> replayDlqRecord(String recordId, String correlationId, String bearerToken);
}
