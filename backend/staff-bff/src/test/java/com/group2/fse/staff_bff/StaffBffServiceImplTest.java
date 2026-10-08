package com.group2.fse.staff_bff;

import com.group2.fse.staff_bff.client.*;
import com.group2.fse.staff_bff.dto.StaffCustomer360Response;
import com.group2.fse.staff_bff.dto.StaffQueuesSummaryResponse;
import com.group2.fse.staff_bff.service.impl.StaffBffServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffBffServiceImplTest {

    @Mock
    private CustomerServiceClient customerClient;

    @Mock
    private AccountServiceClient accountClient;

    @Mock
    private LedgerServiceClient ledgerClient;

    @Mock
    private TransactionServiceClient transactionClient;

    @Mock
    private AuditServiceClient auditClient;

    @Mock
    private BatchServiceClient batchClient;

    @Mock
    private NotificationServiceClient notificationClient;

    private StaffBffServiceImpl staffBffService;

    @BeforeEach
    void setUp() {
        staffBffService = new StaffBffServiceImpl(
                customerClient,
                accountClient,
                ledgerClient,
                transactionClient,
                auditClient,
                batchClient,
                notificationClient,
                Executors.newFixedThreadPool(4)
        );
    }

    @Test
    void testGetCustomer360AggregatesSuccessfully() {
        Long customerId = 1L;

        when(customerClient.fetchCustomerProfile(eq(customerId), any(), any()))
                .thenReturn(Map.of(
                        "displayName", "John Doe",
                        "username", "john_doe",
                        "email", "john@example.com",
                        "mobileNumber", "+639171234567",
                        "kycStatus", "VERIFIED",
                        "kycTier", "TIER_3_FULL"
                ));

        when(customerClient.fetchCustomer360(eq(customerId), any(), any()))
                .thenReturn(Map.of(
                        "creditScore", 780,
                        "riskScore", "0.12",
                        "recentTransactions", Collections.emptyList(),
                        "spendingBreakdown", Map.of("FOOD", "1250.00")
                ));

        when(accountClient.fetchAccountsByCustomerId(eq(customerId), any(), any()))
                .thenReturn(List.of(
                        Map.of(
                                "accountId", 101L,
                                "accountNumber", "ACC_10000001",
                                "accountType", "SAVINGS",
                                "currency", "PHP",
                                "status", "ACTIVE"
                        )
                ));

        when(ledgerClient.fetchBalance(eq(101L), any(), any()))
                .thenReturn(Map.of(
                        "availableBalance", "49625.00",
                        "ledgerBalance", "49625.00",
                        "heldAmount", "0.00"
                ));

        when(accountClient.fetchAccountFlags(eq(101L), any(), any()))
                .thenReturn(Collections.emptyList());

        StaffCustomer360Response response = staffBffService.getCustomer360(customerId, "corr-123", "token-xyz");

        assertNotNull(response);
        assertEquals(1L, response.getCustomerId());
        assertEquals("John Doe", response.getDisplayName());
        assertEquals("VERIFIED", response.getKycStatus());
        assertEquals(780, response.getCreditScore());
        assertEquals("0.12", response.getRiskScore());
        assertEquals(1, response.getAccounts().size());
        assertEquals("49625.00", response.getAccounts().getFirst().getAvailableBalance());
        assertEquals("49625.00", response.getTotals().getTotalDeposits());
        assertTrue(response.getPartialFailures().isEmpty());
    }

    @Test
    void testGetCustomer360HandlesDownstreamFailureGracefully() {
        Long customerId = 2L;

        // Simulate customer-service failure (returns empty / throws)
        when(customerClient.fetchCustomerProfile(eq(customerId), any(), any()))
                .thenThrow(new RuntimeException("Customer service timeout"));

        when(customerClient.fetchCustomer360(eq(customerId), any(), any()))
                .thenReturn(Collections.emptyMap());

        when(accountClient.fetchAccountsByCustomerId(eq(customerId), any(), any()))
                .thenReturn(Collections.emptyList());

        StaffCustomer360Response response = staffBffService.getCustomer360(customerId, "corr-456", "token-abc");

        assertNotNull(response);
        assertEquals(2L, response.getCustomerId());
        assertEquals("Customer #2", response.getDisplayName());
        // Verify graceful degradation: partial failures list contains the failed component
        assertFalse(response.getPartialFailures().isEmpty());
        assertTrue(response.getPartialFailures().contains("customer-profile"));
    }

    @Test
    void testGetQueuesSummaryAggregatesBothQueues() {
        when(customerClient.fetchPendingKycUpdateRequests(any(), any()))
                .thenReturn(List.of(Map.of("requestId", 10, "status", "PENDING")));

        when(accountClient.fetchPendingClosureRequests(any(), any()))
                .thenReturn(List.of(
                        Map.of("closureId", 20, "accountNumber", "ACC_10000007"),
                        Map.of("closureId", 21, "accountNumber", "ACC_10000008")
                ));

        StaffQueuesSummaryResponse response = staffBffService.getQueuesSummary("corr-789", "token-123");

        assertNotNull(response);
        assertEquals(1, response.getTotalPendingKyc());
        assertEquals(2, response.getTotalPendingClosures());
    }
}
