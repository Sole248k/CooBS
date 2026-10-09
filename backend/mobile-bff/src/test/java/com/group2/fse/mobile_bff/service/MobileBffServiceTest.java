package com.group2.fse.mobile_bff.service;

import com.group2.fse.mobile_bff.client.AccountServiceClient;
import com.group2.fse.mobile_bff.client.CustomerServiceClient;
import com.group2.fse.mobile_bff.client.NotificationServiceClient;
import com.group2.fse.mobile_bff.client.TransactionServiceClient;
import com.group2.fse.mobile_bff.dto.*;
import com.group2.fse.mobile_bff.exception.ApiException;
import com.group2.fse.mobile_bff.service.impl.MobileBffServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MobileBffServiceTest {

    @Mock
    private CustomerServiceClient customerServiceClient;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    @Mock
    private TransactionServiceClient transactionServiceClient;

    private MobileBffService mobileBffService;

    @BeforeEach
    void setUp() {
        // Use synchronous executor for deterministic unit test execution
        mobileBffService = new MobileBffServiceImpl(
                customerServiceClient,
                accountServiceClient,
                notificationServiceClient,
                transactionServiceClient,
                new SyncTaskExecutor()
        );
    }

    @Test
    void testGetDashboard_AllHealthy() {
        when(customerServiceClient.fetchMemberProfile(eq(1L), any(), any()))
                .thenReturn(MobileMemberSummary.builder()
                        .customerId(1L)
                        .displayName("John D.")
                        .email("john.doe@example.com")
                        .kycStatus("VERIFIED")
                        .build());

        when(accountServiceClient.fetchCustomerAccounts(eq(1L), any()))
                .thenReturn(List.of(
                        MobileAccountSummary.builder()
                                .accountId(1L)
                                .accountNumber("ACC_10000001")
                                .accountType("SAVINGS")
                                .currency("PHP")
                                .availableBalance("50000.00")
                                .formattedBalance("₱50,000.00")
                                .status("ACTIVE")
                                .build()
                ));

        when(notificationServiceClient.fetchUnreadCount(eq(1L), any(), any()))
                .thenReturn(Optional.of(3));

        when(transactionServiceClient.fetchRecentTransactions(eq(1L), any(), any()))
                .thenReturn(List.of(
                        MobileRecentTransaction.builder()
                                .transactionId("tx-1")
                                .referenceNo("TX-1001")
                                .amount("2500.00")
                                .formattedAmount("-₱2,500.00")
                                .currency("PHP")
                                .status("POSTED")
                                .build()
                ));

        MobileDashboardResponse response = mobileBffService.getDashboard(1L, "corr-1", "mock-token");

        assertNotNull(response);
        assertEquals("John D.", response.getMember().getDisplayName());
        assertEquals("ACC_10000001", response.getPrimaryAccount().getAccountNumber());
        assertEquals(3, response.getUnreadNotifications());
        assertEquals(1, response.getRecentTransactions().size());
        assertTrue(response.getUnavailable().isEmpty());
        assertNotNull(response.getAsOf());
    }

    @Test
    void testGetDashboard_GracefulDegradation_NotificationDown() {
        when(customerServiceClient.fetchMemberProfile(eq(1L), any(), any()))
                .thenReturn(MobileMemberSummary.builder().customerId(1L).displayName("John D.").build());

        when(accountServiceClient.fetchCustomerAccounts(eq(1L), any()))
                .thenReturn(List.of(
                        MobileAccountSummary.builder().accountId(1L).accountNumber("ACC_10000001").status("ACTIVE").accountType("SAVINGS").build()
                ));

        // Notifications fail
        when(notificationServiceClient.fetchUnreadCount(eq(1L), any(), any()))
                .thenReturn(Optional.empty());

        when(transactionServiceClient.fetchRecentTransactions(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        MobileDashboardResponse response = mobileBffService.getDashboard(1L, "corr-1", "mock-token");

        assertNotNull(response);
        assertEquals(0, response.getUnreadNotifications());
        assertTrue(response.getUnavailable().contains("notification-service"));
    }

    @Test
    void testGetTimeline_ReturnsTimelineResponse() {
        MobileTimelineResponse mockTimeline = MobileTimelineResponse.builder()
                .transactionId("tx-123")
                .trackingReference("TX-20261003-8F3A21C0")
                .status("PROCESSING")
                .view("CUSTOMER")
                .currentStage(2)
                .totalStages(3)
                .build();

        when(transactionServiceClient.fetchTimeline(eq("TX-20261003-8F3A21C0"), any(), any()))
                .thenReturn(mockTimeline);

        MobileTimelineResponse result = mobileBffService.getTimeline("TX-20261003-8F3A21C0", "corr-1", "mock-token");

        assertNotNull(result);
        assertEquals("TX-20261003-8F3A21C0", result.getTrackingReference());
        assertEquals("CUSTOMER", result.getView());
    }

    @Test
    void testGetTimeline_AuthorizedCustomer_ReturnsTimelineResponse() {
        MobileTimelineResponse mockTimeline = MobileTimelineResponse.builder()
                .transactionId("tx-123")
                .trackingReference("TX-20261003-8F3A21C0")
                .status("PROCESSING")
                .view("CUSTOMER")
                .currentStage(2)
                .totalStages(3)
                .build();

        // Transaction belongs to account 10L
        when(transactionServiceClient.fetchTransactionDetail(eq("TX-20261003-8F3A21C0"), any(), any()))
                .thenReturn(Map.of("sourceAccountId", 10L, "destinationAccountNumber", "ACC_20000002"));

        // Customer 1 owns account 10L
        when(accountServiceClient.fetchCustomerAccounts(eq(1L), any()))
                .thenReturn(List.of(
                        MobileAccountSummary.builder().accountId(10L).accountNumber("ACC_10000001").status("ACTIVE").build()
                ));

        when(transactionServiceClient.fetchTimeline(eq("TX-20261003-8F3A21C0"), any(), any()))
                .thenReturn(mockTimeline);

        MobileTimelineResponse result = mobileBffService.getTimeline("TX-20261003-8F3A21C0", 1L, "corr-1", "mock-token");

        assertNotNull(result);
        assertEquals("TX-20261003-8F3A21C0", result.getTrackingReference());
    }

    @Test
    void testGetTimeline_UnauthorizedCustomer_ThrowsForbidden() {
        // Transaction belongs to account 999L and ACC_8888
        when(transactionServiceClient.fetchTransactionDetail(eq("TX-20261003-8F3A21C0"), any(), any()))
                .thenReturn(Map.of("sourceAccountId", 999L, "destinationAccountNumber", "ACC_8888"));

        // Customer 1 only owns account 10L
        when(accountServiceClient.fetchCustomerAccounts(eq(1L), any()))
                .thenReturn(List.of(
                        MobileAccountSummary.builder().accountId(10L).accountNumber("ACC_10000001").status("ACTIVE").build()
                ));

        ApiException ex = assertThrows(ApiException.class, () ->
                mobileBffService.getTimeline("TX-20261003-8F3A21C0", 1L, "corr-1", "mock-token"));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void testSubmitRemittance_WithAccountNumberResolution() {
        MobileRemittanceRequest request = MobileRemittanceRequest.builder()
                .sourceAccountNumber("ACC_10000001")
                .destinationAccountNumber("ACC_10000002")
                .destinationBankCode("COOBS")
                .amount(new BigDecimal("1500.00"))
                .currency("PHP")
                .category("TRANSFER")
                .remarks("Test")
                .build();

        when(accountServiceClient.fetchAccountByNumber(eq("ACC_10000001"), any()))
                .thenReturn(Map.of("accountId", 1L, "customerId", 1L, "status", "ACTIVE"));

        when(transactionServiceClient.initiateTransfer(any(), any(), any(), any()))
                .thenReturn(Map.of(
                        "transactionId", "txn-abc",
                        "referenceNo", "TX-20261007-9999",
                        "status", "INITIATED",
                        "amount", "1500.00",
                        "currency", "PHP",
                        "createdAt", "2026-10-07T12:00:00Z"
                ));

        MobileRemittanceResponse response = mobileBffService.submitRemittance(request, "idemp-1", 1L, "corr-1", "mock-token");

        assertNotNull(response);
        assertEquals("TX-20261007-9999", response.getReferenceNo());
        assertEquals("INITIATED", response.getStatus());
        assertEquals("/api/v1/mobile/transactions/TX-20261007-9999/timeline", response.getTimelineUrl());
    }

    @Test
    void testSubmitRemittance_WrongCustomerAccount_ThrowsForbidden() {
        MobileRemittanceRequest request = MobileRemittanceRequest.builder()
                .sourceAccountNumber("ACC_10000002")
                .destinationAccountNumber("ACC_10000003")
                .amount(new BigDecimal("500.00"))
                .build();

        // Account belongs to customer 999, not 1
        when(accountServiceClient.fetchAccountByNumber(eq("ACC_10000002"), any()))
                .thenReturn(Map.of("accountId", 2L, "customerId", 999L, "status", "ACTIVE"));

        assertThrows(ApiException.class, () ->
                mobileBffService.submitRemittance(request, "idemp-1", 1L, "corr-1", "mock-token"));
    }

    @Test
    void testSubmitRemittance_DirectSourceAccountId_WrongCustomerAccount_ThrowsForbidden() {
        MobileRemittanceRequest request = MobileRemittanceRequest.builder()
                .sourceAccountId(999L)
                .destinationAccountNumber("ACC_10000003")
                .amount(new BigDecimal("500.00"))
                .build();

        // Account 999 belongs to customer 888, not caller 1
        when(accountServiceClient.fetchAccountById(eq(999L), any()))
                .thenReturn(Map.of("accountId", 999L, "customerId", 888L, "status", "ACTIVE"));

        ApiException ex = assertThrows(ApiException.class, () ->
                mobileBffService.submitRemittance(request, "idemp-1", 1L, "corr-1", "mock-token"));
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void testSubmitRemittance_SelfTransferSameAccount_ThrowsBadRequest() {
        MobileRemittanceRequest request = MobileRemittanceRequest.builder()
                .sourceAccountNumber("ACC_10000001")
                .destinationAccountNumber("ACC_10000001")
                .amount(new BigDecimal("500.00"))
                .build();

        ApiException ex = assertThrows(ApiException.class, () ->
                mobileBffService.submitRemittance(request, "idemp-1", 1L, "corr-1", "mock-token"));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("cannot be the same"));
    }

    @Test
    void testSubmitRemittance_AmountExceedsCeiling_ThrowsBadRequest() {
        MobileRemittanceRequest request = MobileRemittanceRequest.builder()
                .sourceAccountNumber("ACC_10000001")
                .destinationAccountNumber("ACC_10000002")
                .amount(new BigDecimal("600000.00"))
                .build();

        ApiException ex = assertThrows(ApiException.class, () ->
                mobileBffService.submitRemittance(request, "idemp-1", 1L, "corr-1", "mock-token"));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("ceiling"));
    }

    @Test
    void testSubmitRemittance_RemarksSanitization_StripsHtmlAndControlChars() {
        MobileRemittanceRequest request = MobileRemittanceRequest.builder()
                .sourceAccountNumber("ACC_10000001")
                .destinationAccountNumber("ACC_10000002")
                .amount(new BigDecimal("100.00"))
                .remarks("<script>alert('xss')</script>\r\nPayment")
                .build();

        when(accountServiceClient.fetchAccountByNumber(eq("ACC_10000001"), any()))
                .thenReturn(Map.of("accountId", 1L, "customerId", 1L, "status", "ACTIVE"));

        when(transactionServiceClient.initiateTransfer(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Map<String, Object> payload = invocation.getArgument(0);
                    assertEquals("scriptalert('xss')/script  Payment", payload.get("remarks"));
                    return Map.of(
                            "transactionId", "txn-1",
                            "referenceNo", "TX-1",
                            "status", "INITIATED",
                            "amount", "100.00",
                            "currency", "PHP"
                    );
                });

        MobileRemittanceResponse res = mobileBffService.submitRemittance(request, "idemp-1", 1L, "corr-1", "mock-token");
        assertNotNull(res);
    }

    @Test
    void testSubmitRemittance_InactiveSourceAccount_ThrowsUnprocessableEntity() {
        MobileRemittanceRequest request = MobileRemittanceRequest.builder()
                .sourceAccountNumber("ACC_10000001")
                .destinationAccountNumber("ACC_10000002")
                .amount(new BigDecimal("100.00"))
                .build();

        when(accountServiceClient.fetchAccountByNumber(eq("ACC_10000001"), any()))
                .thenReturn(Map.of("accountId", 1L, "customerId", 1L, "status", "FROZEN"));

        ApiException ex = assertThrows(ApiException.class, () ->
                mobileBffService.submitRemittance(request, "idemp-1", 1L, "corr-1", "mock-token"));
        assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertTrue(ex.getMessage().contains("not active"));
    }

    @Test
    void testGetDashboard_ZeroAccounts_DoesNotMarkAccountServiceUnavailable() {
        when(customerServiceClient.fetchMemberProfile(eq(1L), any(), any()))
                .thenReturn(MobileMemberSummary.builder().customerId(1L).displayName("Jane Doe").kycStatus("VERIFIED").build());

        // Clean 200 OK with zero accounts
        when(accountServiceClient.fetchCustomerAccounts(eq(1L), any()))
                .thenReturn(Collections.emptyList());

        when(notificationServiceClient.fetchUnreadCount(eq(1L), any(), any()))
                .thenReturn(Optional.of(0));

        when(transactionServiceClient.fetchRecentTransactions(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        MobileDashboardResponse response = mobileBffService.getDashboard(1L, "corr-1", "mock-token");

        assertNotNull(response);
        assertFalse(response.getUnavailable().contains("account-service"));
    }

    @Test
    void testGetDashboard_AccountServiceDown_MarksAccountServiceUnavailable() {
        when(customerServiceClient.fetchMemberProfile(eq(1L), any(), any()))
                .thenReturn(MobileMemberSummary.builder().customerId(1L).displayName("Jane Doe").kycStatus("VERIFIED").build());

        // account-service is down and throws
        when(accountServiceClient.fetchCustomerAccounts(eq(1L), any()))
                .thenThrow(new RuntimeException("Connection refused to account-service"));

        when(notificationServiceClient.fetchUnreadCount(eq(1L), any(), any()))
                .thenReturn(Optional.of(0));

        when(transactionServiceClient.fetchRecentTransactions(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        MobileDashboardResponse response = mobileBffService.getDashboard(1L, "corr-1", "mock-token");

        assertNotNull(response);
        assertTrue(response.getUnavailable().contains("account-service"));
    }

    @Test
    void testGetTimeline_WithViewParameter_ForwardsToClient() {
        MobileTimelineResponse mockTimeline = MobileTimelineResponse.builder()
                .trackingReference("TX-999")
                .view("STAFF")
                .totalStages(11)
                .build();

        when(transactionServiceClient.fetchTimeline(eq("TX-999"), eq("STAFF"), any(), any()))
                .thenReturn(mockTimeline);

        MobileTimelineResponse res = mobileBffService.getTimeline("TX-999", null, "STAFF", "corr-1", "mock-token");
        assertNotNull(res);
        assertEquals("STAFF", res.getView());
        assertEquals(11, res.getTotalStages());
    }
}
