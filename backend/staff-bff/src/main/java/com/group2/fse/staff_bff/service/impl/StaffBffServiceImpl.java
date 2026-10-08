package com.group2.fse.staff_bff.service.impl;

import com.group2.fse.staff_bff.client.*;
import com.group2.fse.staff_bff.dto.StaffAccountSummary;
import com.group2.fse.staff_bff.dto.StaffAdminOverviewResponse;
import com.group2.fse.staff_bff.dto.StaffCustomer360Response;
import com.group2.fse.staff_bff.dto.StaffQueuesSummaryResponse;
import com.group2.fse.staff_bff.service.StaffBffService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class StaffBffServiceImpl implements StaffBffService {

    private final CustomerServiceClient customerClient;
    private final AccountServiceClient accountClient;
    private final LedgerServiceClient ledgerClient;
    private final TransactionServiceClient transactionClient;
    private final AuditServiceClient auditClient;
    private final BatchServiceClient batchClient;
    private final NotificationServiceClient notificationClient;
    private final Executor bffExecutor;

    @Autowired
    public StaffBffServiceImpl(
            CustomerServiceClient customerClient,
            AccountServiceClient accountClient,
            LedgerServiceClient ledgerClient,
            TransactionServiceClient transactionClient,
            AuditServiceClient auditClient,
            BatchServiceClient batchClient,
            NotificationServiceClient notificationClient,
            @Qualifier("staffBffTaskExecutor") Executor bffExecutor) {
        this.customerClient = customerClient;
        this.accountClient = accountClient;
        this.ledgerClient = ledgerClient;
        this.transactionClient = transactionClient;
        this.auditClient = auditClient;
        this.batchClient = batchClient;
        this.notificationClient = notificationClient;
        this.bffExecutor = bffExecutor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StaffCustomer360Response getCustomer360(Long customerId, String correlationId, String bearerToken) {
        List<String> partialFailures = new CopyOnWriteArrayList<>();

        // 1. Fetch Customer 360 / Profile in parallel
        CompletableFuture<Map<String, Object>> profileFuture = CompletableFuture.supplyAsync(
                () -> customerClient.fetchCustomerProfile(customerId, bearerToken, correlationId),
                bffExecutor
        ).exceptionally(ex -> {
            log.warn("Profile fetch failed for customer ID {}: {}", customerId, ex.getMessage());
            partialFailures.add("customer-profile");
            return Collections.emptyMap();
        });

        CompletableFuture<Map<String, Object>> c360Future = CompletableFuture.supplyAsync(
                () -> customerClient.fetchCustomer360(customerId, bearerToken, correlationId),
                bffExecutor
        ).exceptionally(ex -> {
            log.warn("C360 fetch failed for customer ID {}: {}", customerId, ex.getMessage());
            partialFailures.add("customer-360");
            return Collections.emptyMap();
        });

        CompletableFuture<List<Map<String, Object>>> accountsFuture = CompletableFuture.supplyAsync(
                () -> accountClient.fetchAccountsByCustomerId(customerId, bearerToken, correlationId),
                bffExecutor
        ).exceptionally(ex -> {
            log.warn("Accounts fetch failed for customer ID {}: {}", customerId, ex.getMessage());
            partialFailures.add("account-service");
            return Collections.emptyList();
        });

        CompletableFuture.allOf(profileFuture, c360Future, accountsFuture).join();

        Map<String, Object> profile = profileFuture.join();
        Map<String, Object> c360 = c360Future.join();
        List<Map<String, Object>> rawAccounts = accountsFuture.join();

        // 2. Fetch Ledger Balances and Flags for each account in parallel
        List<CompletableFuture<StaffAccountSummary>> summaryFutures = rawAccounts.stream().map(acc ->
                CompletableFuture.supplyAsync(() -> {
                    Long accId = Long.valueOf(acc.get("accountId").toString());
                    Map<String, Object> balanceData = ledgerClient.fetchBalance(accId, bearerToken, correlationId);
                    List<Map<String, Object>> flags = accountClient.fetchAccountFlags(accId, bearerToken, correlationId);

                    List<String> flagDescriptions = flags.stream()
                            .map(f -> f.get("reason") != null ? f.get("reason").toString() : "FLAGGED")
                            .toList();

                    String availBal = balanceData.get("availableBalance") != null ?
                            balanceData.get("availableBalance").toString() :
                            (acc.get("availableBalance") != null ? acc.get("availableBalance").toString() : "0.00");

                    String ledgerBal = balanceData.get("ledgerBalance") != null ?
                            balanceData.get("ledgerBalance").toString() :
                            (acc.get("ledgerBalance") != null ? acc.get("ledgerBalance").toString() : availBal);

                    String heldAmt = balanceData.get("heldAmount") != null ?
                            balanceData.get("heldAmount").toString() :
                            (acc.get("heldAmount") != null ? acc.get("heldAmount").toString() : "0.00");

                    return StaffAccountSummary.builder()
                            .accountId(accId)
                            .accountNumber(acc.get("accountNumber") != null ? acc.get("accountNumber").toString() : "")
                            .accountType(acc.get("accountType") != null ? acc.get("accountType").toString() : "SAVINGS")
                            .currency(acc.get("currency") != null ? acc.get("currency").toString() : "PHP")
                            .status(acc.get("status") != null ? acc.get("status").toString() : "ACTIVE")
                            .availableBalance(formatMoney(availBal))
                            .ledgerBalance(formatMoney(ledgerBal))
                            .heldAmount(formatMoney(heldAmt))
                            .outstandingPrincipal(acc.get("outstandingPrincipal") != null ? formatMoney(acc.get("outstandingPrincipal").toString()) : null)
                            .nextDueDate(acc.get("nextDueDate") != null ? acc.get("nextDueDate").toString() : null)
                            .nextDueAmount(acc.get("nextDueAmount") != null ? formatMoney(acc.get("nextDueAmount").toString()) : null)
                            .activeFlags(flagDescriptions)
                            .build();
                }, bffExecutor)
        ).toList();

        List<StaffAccountSummary> accountSummaries = summaryFutures.stream()
                .map(CompletableFuture::join)
                .toList();

        // 3. Compute Totals
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalLoans = BigDecimal.ZERO;
        for (StaffAccountSummary acc : accountSummaries) {
            BigDecimal amt = parseDecimal(acc.getAvailableBalance());
            if ("LOAN".equalsIgnoreCase(acc.getAccountType())) {
                totalLoans = totalLoans.add(parseDecimal(acc.getOutstandingPrincipal() != null ? acc.getOutstandingPrincipal() : "0.00"));
            } else {
                totalDeposits = totalDeposits.add(amt);
            }
        }

        String displayName = profile.get("displayName") != null ? profile.get("displayName").toString() :
                (c360.get("displayName") != null ? c360.get("displayName").toString() : "Customer #" + customerId);
        String username = profile.get("username") != null ? profile.get("username").toString() : "";
        String email = profile.get("email") != null ? profile.get("email").toString() : "";
        String phone = profile.get("mobileNumber") != null ? profile.get("mobileNumber").toString() :
                (profile.get("phone") != null ? profile.get("phone").toString() : "");
        String kycStatus = profile.get("kycStatus") != null ? profile.get("kycStatus").toString() :
                (c360.get("kycStatus") != null ? c360.get("kycStatus").toString() : "VERIFIED");
        String kycTier = profile.get("kycTier") != null ? profile.get("kycTier").toString() :
                (c360.get("kycTier") != null ? c360.get("kycTier").toString() : "TIER_3_FULL");

        Integer creditScore = c360.get("creditScore") instanceof Number ? ((Number) c360.get("creditScore")).intValue() : 750;
        String riskScore = c360.get("riskScore") != null ? c360.get("riskScore").toString() : "0.15";

        List<Map<String, Object>> recentTx = c360.get("recentTransactions") instanceof List ?
                (List<Map<String, Object>>) c360.get("recentTransactions") : Collections.emptyList();
        Map<String, Object> spending = c360.get("spendingBreakdown") instanceof Map ?
                (Map<String, Object>) c360.get("spendingBreakdown") : Collections.emptyMap();
        List<Map<String, Object>> beneficiaries = c360.get("savedBeneficiaries") instanceof List ?
                (List<Map<String, Object>>) c360.get("savedBeneficiaries") : Collections.emptyList();

        return StaffCustomer360Response.builder()
                .customerId(customerId)
                .displayName(displayName)
                .username(username)
                .email(email)
                .phone(phone)
                .membershipNumber("MEM-" + customerId)
                .kycStatus(kycStatus)
                .kycTier(kycTier)
                .creditScore(creditScore)
                .riskScore(riskScore)
                .accounts(accountSummaries)
                .totals(new StaffCustomer360Response.StaffTotals(formatMoney(totalDeposits.toString()), formatMoney(totalLoans.toString())))
                .recentTransactions(recentTx)
                .spendingBreakdown(spending)
                .savedBeneficiaries(beneficiaries)
                .asOf(Instant.now().toString())
                .partialFailures(partialFailures)
                .build();
    }

    @Override
    public StaffQueuesSummaryResponse getQueuesSummary(String correlationId, String bearerToken) {
        List<String> partialFailures = new CopyOnWriteArrayList<>();

        CompletableFuture<List<Map<String, Object>>> kycFuture = CompletableFuture.supplyAsync(
                () -> customerClient.fetchPendingKycUpdateRequests(bearerToken, correlationId),
                bffExecutor
        ).exceptionally(ex -> {
            log.warn("Failed to fetch pending KYC queue: {}", ex.getMessage());
            partialFailures.add("customer-kyc-queue");
            return Collections.emptyList();
        });

        CompletableFuture<List<Map<String, Object>>> closuresFuture = CompletableFuture.supplyAsync(
                () -> accountClient.fetchPendingClosureRequests(bearerToken, correlationId),
                bffExecutor
        ).exceptionally(ex -> {
            log.warn("Failed to fetch pending closure queue: {}", ex.getMessage());
            partialFailures.add("account-closure-queue");
            return Collections.emptyList();
        });

        CompletableFuture.allOf(kycFuture, closuresFuture).join();

        List<Map<String, Object>> kycRequests = kycFuture.join();
        List<Map<String, Object>> closureRequests = closuresFuture.join();

        return StaffQueuesSummaryResponse.builder()
                .totalPendingKyc(kycRequests.size())
                .totalPendingClosures(closureRequests.size())
                .pendingKycRequests(kycRequests)
                .pendingClosureRequests(closureRequests)
                .partialFailures(partialFailures)
                .build();
    }

    @Override
    public StaffAdminOverviewResponse getAdminOverview(String businessDate, String correlationId, String bearerToken) {
        List<String> partialFailures = new CopyOnWriteArrayList<>();
        String effectiveDate = (businessDate != null && !businessDate.isBlank()) ? businessDate : java.time.LocalDate.now().toString();

        CompletableFuture<List<Map<String, Object>>> batchRunsFuture = CompletableFuture.supplyAsync(
                () -> batchClient.fetchAllBatchRuns(bearerToken, correlationId),
                bffExecutor
        ).exceptionally(ex -> {
            log.warn("Failed to fetch batch runs for admin overview: {}", ex.getMessage());
            partialFailures.add("batch-service");
            return Collections.emptyList();
        });

        CompletableFuture<List<Map<String, Object>>> dlqFuture = CompletableFuture.supplyAsync(
                () -> notificationClient.fetchDlqMessages(bearerToken, correlationId),
                bffExecutor
        ).exceptionally(ex -> {
            log.warn("Failed to fetch DLQ messages for admin overview: {}", ex.getMessage());
            partialFailures.add("notification-dlq");
            return Collections.emptyList();
        });

        CompletableFuture<Map<String, Object>> sealFuture = CompletableFuture.supplyAsync(
                () -> auditClient.fetchDailySeal(effectiveDate, bearerToken, correlationId),
                bffExecutor
        ).exceptionally(ex -> {
            log.warn("Failed to fetch daily seal for {}: {}", effectiveDate, ex.getMessage());
            partialFailures.add("audit-seal");
            return Collections.emptyMap();
        });

        CompletableFuture.allOf(batchRunsFuture, dlqFuture, sealFuture).join();

        List<Map<String, Object>> runs = batchRunsFuture.join();
        List<Map<String, Object>> dlq = dlqFuture.join();
        Map<String, Object> seal = sealFuture.join();

        Map<String, Object> latestRun = runs.isEmpty() ? Collections.emptyMap() : runs.getFirst();

        return StaffAdminOverviewResponse.builder()
                .latestBatchRun(latestRun)
                .deadLetterQueueCount(dlq.size())
                .deadLetterRecords(dlq)
                .latestAuditSeal(seal)
                .partialFailures(partialFailures)
                .build();
    }

    @Override
    public Map<String, Object> executeCashDeposit(Map<String, Object> payload, String idempotencyKey, String correlationId, String bearerToken) {
        return ledgerClient.executeCashDeposit(payload, idempotencyKey, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> executeCashWithdrawal(Map<String, Object> payload, String idempotencyKey, String correlationId, String bearerToken) {
        return ledgerClient.executeCashWithdrawal(payload, idempotencyKey, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> executeAssistedTransfer(Map<String, Object> payload, String idempotencyKey, String correlationId, String bearerToken) {
        return transactionClient.executeAssistedTransfer(payload, idempotencyKey, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> approveKycUpdate(Long requestId, String correlationId, String bearerToken) {
        return customerClient.approveKycUpdateRequest(requestId, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> rejectKycUpdate(Long requestId, String reason, String correlationId, String bearerToken) {
        return customerClient.rejectKycUpdateRequest(requestId, reason, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> approveClosure(Long closureId, String correlationId, String bearerToken) {
        return accountClient.approveClosure(closureId, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> rejectClosure(Long closureId, String reason, String correlationId, String bearerToken) {
        return accountClient.rejectClosure(closureId, reason, bearerToken, correlationId);
    }

    @Override
    public List<Map<String, Object>> getAllAccounts(String correlationId, String bearerToken) {
        return accountClient.fetchAllAccounts(bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> openAccount(Map<String, Object> payload, String correlationId, String bearerToken) {
        return accountClient.openAccount(payload, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> updateAccountStatus(Long accountId, String status, String correlationId, String bearerToken) {
        return accountClient.updateAccountStatus(accountId, status, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> addAccountFlag(Long accountId, String reason, String correlationId, String bearerToken) {
        return accountClient.addFlag(accountId, reason, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> removeAccountFlag(Long accountId, Long flagId, String correlationId, String bearerToken) {
        return accountClient.removeFlag(accountId, flagId, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> getTransactionTimeline(String transactionId, String correlationId, String bearerToken) {
        return transactionClient.fetchStaffTimeline(transactionId, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> requestReversal(String transactionId, String reason, String idempotencyKey, String correlationId, String bearerToken) {
        return transactionClient.requestReversal(transactionId, reason, idempotencyKey, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> getAccountStatement(Long accountId, int page, int size, String correlationId, String bearerToken) {
        return auditClient.fetchAccountStatement(accountId, page, size, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> verifyAccountChain(Long accountId, String correlationId, String bearerToken) {
        return auditClient.verifyChainIntegrity(accountId, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> getDailySeal(String businessDate, String correlationId, String bearerToken) {
        return auditClient.fetchDailySeal(businessDate, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> triggerEodBatch(Map<String, Object> payload, String correlationId, String bearerToken) {
        return batchClient.triggerEodBatch(payload, bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> getBatchRun(Long batchRunId, String correlationId, String bearerToken) {
        return batchClient.fetchBatchRun(batchRunId, bearerToken, correlationId);
    }

    @Override
    public List<Map<String, Object>> getAllBatchRuns(String correlationId, String bearerToken) {
        return batchClient.fetchAllBatchRuns(bearerToken, correlationId);
    }

    @Override
    public List<Map<String, Object>> getDlqMessages(String correlationId, String bearerToken) {
        return notificationClient.fetchDlqMessages(bearerToken, correlationId);
    }

    @Override
    public Map<String, Object> replayDlqRecord(String recordId, String correlationId, String bearerToken) {
        return notificationClient.replayDlqRecord(recordId, bearerToken, correlationId);
    }

    private String formatMoney(String raw) {
        if (raw == null || raw.isBlank()) return "0.00";
        try {
            return new BigDecimal(raw.replaceAll("[^0-9.-]", "")).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            return raw;
        }
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(raw.replaceAll("[^0-9.-]", ""));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
