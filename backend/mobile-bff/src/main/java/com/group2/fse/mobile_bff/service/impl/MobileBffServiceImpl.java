package com.group2.fse.mobile_bff.service.impl;

import com.group2.fse.mobile_bff.client.AccountServiceClient;
import com.group2.fse.mobile_bff.client.CustomerServiceClient;
import com.group2.fse.mobile_bff.client.NotificationServiceClient;
import com.group2.fse.mobile_bff.client.TransactionServiceClient;
import com.group2.fse.mobile_bff.dto.*;
import com.group2.fse.mobile_bff.exception.ApiException;
import com.group2.fse.mobile_bff.service.MobileBffService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class MobileBffServiceImpl implements MobileBffService {

    private final CustomerServiceClient customerServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final TransactionServiceClient transactionServiceClient;
    private final Executor bffTaskExecutor;

    @Autowired
    public MobileBffServiceImpl(
            CustomerServiceClient customerServiceClient,
            AccountServiceClient accountServiceClient,
            NotificationServiceClient notificationServiceClient,
            TransactionServiceClient transactionServiceClient,
            @Qualifier("bffTaskExecutor") Executor bffTaskExecutor) {
        this.customerServiceClient = customerServiceClient;
        this.accountServiceClient = accountServiceClient;
        this.notificationServiceClient = notificationServiceClient;
        this.transactionServiceClient = transactionServiceClient;
        this.bffTaskExecutor = bffTaskExecutor;
    }

    @Override
    public MobileDashboardResponse getDashboard(Long customerId, String correlationId, String bearerToken) {
        long startTime = System.currentTimeMillis();
        List<String> unavailable = new CopyOnWriteArrayList<>();

        // 1. Parallel fan-out
        CompletableFuture<MobileMemberSummary> memberFuture = CompletableFuture.supplyAsync(
                () -> customerServiceClient.fetchMemberProfile(customerId, bearerToken, correlationId),
                bffTaskExecutor
        ).exceptionally(ex -> {
            log.warn("Member fetch failed for customer [{}]: {}", customerId, ex.getMessage());
            unavailable.add("customer-service");
            return MobileMemberSummary.builder()
                    .customerId(customerId)
                    .displayName("Member")
                    .email("")
                    .kycStatus("VERIFIED")
                    .build();
        });

        CompletableFuture<List<MobileAccountSummary>> accountsFuture = CompletableFuture.supplyAsync(
                () -> accountServiceClient.fetchCustomerAccounts(customerId, correlationId),
                bffTaskExecutor
        ).exceptionally(ex -> {
            log.warn("Accounts fetch failed for customer [{}]: {}", customerId, ex.getMessage());
            unavailable.add("account-service");
            return Collections.emptyList();
        });

        CompletableFuture<Optional<Integer>> notificationFuture = CompletableFuture.supplyAsync(
                () -> notificationServiceClient.fetchUnreadCount(customerId, bearerToken, correlationId),
                bffTaskExecutor
        ).exceptionally(ex -> {
            log.warn("Notifications fetch failed for customer [{}]: {}", customerId, ex.getMessage());
            unavailable.add("notification-service");
            return Optional.empty();
        });

        CompletableFuture<List<MobileRecentTransaction>> transactionsFuture = CompletableFuture.supplyAsync(
                () -> transactionServiceClient.fetchRecentTransactions(customerId, bearerToken, correlationId),
                bffTaskExecutor
        ).exceptionally(ex -> {
            log.warn("Transactions fetch failed for customer [{}]: {}", customerId, ex.getMessage());
            unavailable.add("transaction-service");
            return Collections.emptyList();
        });

        // 2. Await all futures with 2500ms SLA budget
        try {
            CompletableFuture.allOf(memberFuture, accountsFuture, notificationFuture, transactionsFuture)
                    .get(2500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.warn("Dashboard fan-out timed out after 2500ms for customer [{}], gathering partial results", customerId);
        } catch (Exception ex) {
            log.warn("Dashboard fan-out exception for customer [{}]: {}", customerId, ex.getMessage());
        }

        MobileMemberSummary member = safeJoin(memberFuture, MobileMemberSummary.builder()
                .customerId(customerId)
                .displayName("Member")
                .email("")
                .kycStatus("VERIFIED")
                .build());

        List<MobileAccountSummary> accounts = safeJoin(accountsFuture, Collections.emptyList());
        MobileAccountSummary primaryAccount = selectPrimaryAccount(accounts);

        Optional<Integer> unreadOpt = safeJoin(notificationFuture, Optional.empty());
        int unreadNotifications = 0;
        if (unreadOpt.isPresent()) {
            unreadNotifications = unreadOpt.get();
        } else {
            if (!unavailable.contains("notification-service")) {
                unavailable.add("notification-service");
            }
        }

        List<MobileRecentTransaction> recentTransactions = safeJoin(transactionsFuture, Collections.emptyList());

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Aggregated dashboard for Customer [{}] in {}ms (unavailable: {})", customerId, elapsed, unavailable);

        return MobileDashboardResponse.builder()
                .member(member)
                .primaryAccount(primaryAccount)
                .unreadNotifications(unreadNotifications)
                .recentTransactions(recentTransactions)
                .unavailable(unavailable)
                .asOf(Instant.now().toString())
                .build();
    }

    @Override
    public MobileTimelineResponse getTimeline(String trackingReference, String correlationId, String bearerToken) {
        return getTimeline(trackingReference, null, null, correlationId, bearerToken);
    }

    @Override
    public MobileTimelineResponse getTimeline(String trackingReference, Long customerId, String correlationId, String bearerToken) {
        return getTimeline(trackingReference, customerId, null, correlationId, bearerToken);
    }

    @Override
    public MobileTimelineResponse getTimeline(String trackingReference, Long customerId, String view, String correlationId, String bearerToken) {
        log.info("Retrieving mobile timeline for trackingReference [{}] by customer [{}] (view={})", trackingReference, customerId, view);

        // Security Guard (Timeline IDOR Prevention):
        // If customerId is provided, verify they are an authorized party (sender or receiver)
        if (customerId != null && customerId > 0) {
            Map<String, Object> detail = transactionServiceClient.fetchTransactionDetail(trackingReference, bearerToken, correlationId);
            if (detail != null) {
                List<MobileAccountSummary> accounts = accountServiceClient.fetchCustomerAccounts(customerId, correlationId);
                Set<Long> userAccountIds = accounts != null ? accounts.stream()
                        .map(MobileAccountSummary::getAccountId)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet()) : Collections.emptySet();

                Set<String> userAccountNumbers = accounts != null ? accounts.stream()
                        .map(MobileAccountSummary::getAccountNumber)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet()) : Collections.emptySet();

                Long srcAccountId = detail.get("sourceAccountId") != null ?
                        Long.valueOf(detail.get("sourceAccountId").toString()) : null;
                String dstAccountNum = detail.get("destinationAccountNumber") != null ?
                        detail.get("destinationAccountNumber").toString() : null;

                boolean isSender = srcAccountId != null && userAccountIds.contains(srcAccountId);
                boolean isReceiver = dstAccountNum != null && userAccountNumbers.contains(dstAccountNum);

                if (!isSender && !isReceiver) {
                    log.warn("Timeline IDOR attempt blocked: Customer [{}] attempted to view transaction [{}] without participation",
                            customerId, trackingReference);
                    throw new ApiException(HttpStatus.FORBIDDEN, "Transaction does not belong to authenticated member");
                }
            }
        }

        if (view != null && !view.isBlank()) {
            return transactionServiceClient.fetchTimeline(trackingReference, view, bearerToken, correlationId);
        }
        return transactionServiceClient.fetchTimeline(trackingReference, bearerToken, correlationId);
    }

    @Override
    public MobileRemittanceOptionsResponse getRemittanceOptions() {
        return MobileRemittanceOptionsResponse.builder()
                .currency("PHP")
                .scale(2)
                .limits(MobileRemittanceOptionsResponse.Limits.builder()
                        .minAmount(new BigDecimal(MobileRemittanceRequest.MIN_AMOUNT))
                        .maxAmount(new BigDecimal(MobileRemittanceRequest.MAX_AMOUNT))
                        .build())
                .build();
    }

    @Override
    public MobileRemittanceResponse submitRemittance(MobileRemittanceRequest request, String idempotencyKey, Long customerId, String correlationId, String bearerToken) {
        log.info("Intake remittance for Customer [{}]: dstAccount={}, amount={}",
                customerId, request.getDestinationAccountNumber(), request.getAmount());

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer amount must be greater than zero");
        }
        if (request.getAmount().compareTo(new BigDecimal(MobileRemittanceRequest.MAX_AMOUNT)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer amount exceeds single-transaction ceiling of ₱500,000.00");
        }

        if (request.getSourceAccountNumber() != null &&
                request.getSourceAccountNumber().trim().equalsIgnoreCase(request.getDestinationAccountNumber().trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Source account and destination account cannot be the same");
        }

        Long sourceAccountId = request.getSourceAccountId();

        // Security Guard (BOLA / IDOR Prevention):
        // If sourceAccountId is provided, strictly verify that it belongs to the authenticated customer
        if (sourceAccountId != null) {
            Map<String, Object> accMap = accountServiceClient.fetchAccountById(sourceAccountId, correlationId);
            if (accMap == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Source account not found: " + sourceAccountId);
            }

            if (accMap.get("accountNumber") != null &&
                    accMap.get("accountNumber").toString().trim().equalsIgnoreCase(request.getDestinationAccountNumber().trim())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Source account and destination account cannot be the same");
            }

            String status = accMap.get("status") != null ? accMap.get("status").toString() : "";
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Source account is not active (current status: " + status + ")");
            }

            Object ownerIdObj = accMap.get("customerId");
            if (ownerIdObj != null) {
                Long ownerId = Long.valueOf(ownerIdObj.toString());
                if (!ownerId.equals(customerId)) {
                    log.warn("BOLA / IDOR attempt blocked: Customer [{}] attempted to debit account [{}] owned by [{}]",
                            customerId, sourceAccountId, ownerId);
                    throw new ApiException(HttpStatus.FORBIDDEN, "Source account does not belong to authenticated member");
                }
            }
        } else if (request.getSourceAccountNumber() != null && !request.getSourceAccountNumber().isBlank()) {
            // Resolve and verify sourceAccountId from sourceAccountNumber
            Map<String, Object> accMap = accountServiceClient.fetchAccountByNumber(request.getSourceAccountNumber(), correlationId);
            if (accMap == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Source account not found: " + request.getSourceAccountNumber());
            }

            String status = accMap.get("status") != null ? accMap.get("status").toString() : "";
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Source account is not active (current status: " + status + ")");
            }

            Object ownerIdObj = accMap.get("customerId");
            if (ownerIdObj != null) {
                Long ownerId = Long.valueOf(ownerIdObj.toString());
                if (!ownerId.equals(customerId)) {
                    log.warn("BOLA / IDOR attempt blocked: Customer [{}] attempted to debit account number [{}] owned by [{}]",
                            customerId, request.getSourceAccountNumber(), ownerId);
                    throw new ApiException(HttpStatus.FORBIDDEN, "Source account does not belong to authenticated member");
                }
            }

            Object accIdObj = accMap.get("accountId");
            if (accIdObj != null) {
                sourceAccountId = Long.valueOf(accIdObj.toString());
            }
        }

        // If sourceAccountId is still null, default to customer's verified primary account
        if (sourceAccountId == null) {
            List<MobileAccountSummary> accounts = accountServiceClient.fetchCustomerAccounts(customerId, correlationId);
            MobileAccountSummary primary = selectPrimaryAccount(accounts);
            if (primary.getAccountId() != null && primary.getAccountId() > 0) {
                if (!"ACTIVE".equalsIgnoreCase(primary.getStatus())) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Source primary account is not active (current status: " + primary.getStatus() + ")");
                }
                if (primary.getAccountNumber() != null &&
                        primary.getAccountNumber().trim().equalsIgnoreCase(request.getDestinationAccountNumber().trim())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Source account and destination account cannot be the same");
                }
                sourceAccountId = primary.getAccountId();
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Source account could not be resolved for transfer");
            }
        }

        // Sanitize remarks: strip dangerous injection characters and clamp length
        String rawRemarks = request.getRemarks() != null ? request.getRemarks() : "";
        String sanitizedRemarks = rawRemarks
                .replaceAll("[<>]", "")
                .replaceAll("[\\r\\n\\t]", " ")
                .trim();
        if (sanitizedRemarks.length() > 150) {
            sanitizedRemarks = sanitizedRemarks.substring(0, 150);
        }

        // Prepare downstream transfer request
        Map<String, Object> transferPayload = new HashMap<>();
        transferPayload.put("sourceAccountId", sourceAccountId);
        transferPayload.put("destinationAccountNumber", request.getDestinationAccountNumber().trim());
        transferPayload.put("destinationBankCode", request.getDestinationBankCode() != null ? request.getDestinationBankCode().trim() : "COOBS");
        transferPayload.put("amount", request.getAmount().setScale(2, RoundingMode.HALF_UP));
        transferPayload.put("currency", request.getCurrency() != null ? request.getCurrency() : "PHP");
        transferPayload.put("category", request.getCategory() != null ? request.getCategory() : "TRANSFER");
        transferPayload.put("remarks", sanitizedRemarks);

        Map<String, Object> response = transactionServiceClient.initiateTransfer(
                transferPayload, idempotencyKey, bearerToken, correlationId);

        String txnId = response.get("transactionId") != null ? response.get("transactionId").toString() : UUID.randomUUID().toString();
        String refNo = response.get("referenceNo") != null ? response.get("referenceNo").toString() :
                (response.get("trackingReference") != null ? response.get("trackingReference").toString() : txnId);
        String stat = response.get("status") != null ? response.get("status").toString() : "INITIATED";
        String curr = response.get("currency") != null ? response.get("currency").toString() : request.getCurrency();
        String created = response.get("createdAt") != null ? response.get("createdAt").toString() : Instant.now().toString();
        String amountStr = response.get("amount") != null ? response.get("amount").toString() :
                request.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();

        String mobileTimelineUrl = "/api/v1/mobile/transactions/" + refNo + "/timeline";

        return MobileRemittanceResponse.builder()
                .transactionId(txnId)
                .referenceNo(refNo)
                .status(stat)
                .amount(amountStr)
                .currency(curr)
                .createdAt(created)
                .timelineUrl(mobileTimelineUrl)
                .build();
    }

    private MobileAccountSummary selectPrimaryAccount(List<MobileAccountSummary> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return MobileAccountSummary.builder()
                    .accountId(0L)
                    .accountNumber("ACC_00000000")
                    .accountType("SAVINGS")
                    .currency("PHP")
                    .availableBalance("0.00")
                    .formattedBalance("₱0.00")
                    .status("INACTIVE")
                    .build();
        }

        // Prefer active SAVINGS account, else first ACTIVE account, else first account
        return accounts.stream()
                .filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()) && "SAVINGS".equalsIgnoreCase(a.getAccountType()))
                .findFirst()
                .or(() -> accounts.stream().filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus())).findFirst())
                .orElse(accounts.get(0));
    }

    private <T> T safeJoin(CompletableFuture<T> future, T fallback) {
        if (future.isDone()) {
            try {
                return future.join();
            } catch (Exception ex) {
                return fallback;
            }
        }
        return fallback;
    }
}
