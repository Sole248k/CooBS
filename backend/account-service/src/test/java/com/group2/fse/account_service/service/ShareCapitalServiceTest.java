package com.group2.fse.account_service.service;

import com.group2.fse.account_service.dto.sharecapital.*;
import com.group2.fse.account_service.entity.*;
import com.group2.fse.account_service.exception.InsufficientAccountFundsException;
import com.group2.fse.account_service.repository.*;
import com.group2.fse.account_service.service.impl.ShareCapitalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Co-op Share Capital Service Unit Tests")
class ShareCapitalServiceTest {

    @Mock
    private MemberShareCapitalRepository memberShareCapitalRepository;

    @Mock
    private ShareCapitalTransactionRepository shareCapitalTransactionRepository;

    @Mock
    private PatronageDividendPayoutRepository patronageDividendPayoutRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private ShareCapitalServiceImpl shareCapitalService;

    private MemberShareCapital mockShareCapital;
    private Account mockAccount;
    private Balance mockBalance;

    @BeforeEach
    void setUp() {
        mockAccount = Account.builder()
                .accountId(10L)
                .customerId(1001L)
                .accountNumber("ACC_10000001")
                .accountType("SAVINGS")
                .status("ACTIVE")
                .currency("PHP")
                .build();

        mockBalance = Balance.builder()
                .balanceId(10L)
                .account(mockAccount)
                .availableBalance(new BigDecimal("50000.0000"))
                .build();

        mockShareCapital = MemberShareCapital.builder()
                .shareCapitalId(100L)
                .certificateNo("SC-2024-001001")
                .customerId(1001L)
                .account(mockAccount)
                .subscribedShares(500)
                .paidUpShares(250)
                .parValue(new BigDecimal("100.0000"))
                .totalPaidAmount(new BigDecimal("25000.0000"))
                .minimumRequiredShares(20)
                .dividendRate(new BigDecimal("7.25"))
                .membershipType("REGULAR")
                .hasVotingRights(true)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getMemberShareCapital: returns existing portfolio accurately")
    void testGetMemberShareCapital_Existing() {
        when(memberShareCapitalRepository.findByCustomerId(1001L)).thenReturn(Optional.of(mockShareCapital));

        ShareCapitalResponse res = shareCapitalService.getMemberShareCapital(1001L);

        assertNotNull(res);
        assertEquals("SC-2024-001001", res.getCertificateNo());
        assertEquals(250, res.getPaidUpShares());
        assertEquals(500, res.getSubscribedShares());
        assertEquals(new BigDecimal("25000.0000"), res.getTotalPaidAmount());
        assertEquals(new BigDecimal("50000.0000"), res.getSubscribedAmount());
        assertEquals(0.5, res.getCompletionRatio(), 0.001);
        assertTrue(res.getHasVotingRights());
        assertEquals("REGULAR", res.getMembershipType());
    }

    @Test
    @DisplayName("getMemberShareCapital: auto-provisions if not yet existing")
    void testGetMemberShareCapital_AutoProvision() {
        when(memberShareCapitalRepository.findByCustomerId(1002L)).thenReturn(Optional.empty());
        when(accountRepository.findByCustomerId(1002L)).thenReturn(List.of(mockAccount));
        when(memberShareCapitalRepository.save(any(MemberShareCapital.class))).thenAnswer(invocation -> {
            MemberShareCapital entity = invocation.getArgument(0);
            entity.setShareCapitalId(101L);
            return entity;
        });

        ShareCapitalResponse res = shareCapitalService.getMemberShareCapital(1002L);

        assertNotNull(res);
        assertEquals("SC-2024-001002", res.getCertificateNo());
        assertEquals(250, res.getPaidUpShares());
        assertEquals(500, res.getSubscribedShares());
        verify(shareCapitalTransactionRepository, times(1)).save(any(ShareCapitalTransaction.class));
        verify(patronageDividendPayoutRepository, times(2)).save(any(PatronageDividendPayout.class));
    }

    @Test
    @DisplayName("topUpShareCapital: successfully purchases shares from savings account")
    void testTopUpShareCapital_Success() {
        when(memberShareCapitalRepository.findByCustomerId(1001L)).thenReturn(Optional.of(mockShareCapital));
        when(accountRepository.findByCustomerId(1001L)).thenReturn(List.of(mockAccount));
        when(balanceRepository.findByAccount_AccountId(10L)).thenReturn(Optional.of(mockBalance));
        when(memberShareCapitalRepository.save(any(MemberShareCapital.class))).thenAnswer(i -> i.getArgument(0));

        ShareCapitalTopUpRequest req = ShareCapitalTopUpRequest.builder()
                .amount(new BigDecimal("5000.00"))
                .build();

        ShareCapitalTopUpResponse res = shareCapitalService.topUpShareCapital(1001L, req);

        assertNotNull(res);
        assertEquals(50, res.getSharesAdded()); // 5000 / 100 = 50 shares
        assertEquals(300, res.getNewPaidUpShares()); // 250 + 50 = 300
        assertEquals(new BigDecimal("30000.0000"), res.getNewTotalPaidAmount());
        assertEquals(new BigDecimal("45000.0000"), mockBalance.getAvailableBalance()); // 50000 - 5000
        assertTrue(res.getHasVotingRights());
        verify(shareCapitalTransactionRepository, times(1)).save(any(ShareCapitalTransaction.class));
    }

    @Test
    @DisplayName("topUpShareCapital: throws InsufficientAccountFundsException when balance is too low")
    void testTopUpShareCapital_InsufficientFunds() {
        when(memberShareCapitalRepository.findByCustomerId(1001L)).thenReturn(Optional.of(mockShareCapital));
        when(accountRepository.findByCustomerId(1001L)).thenReturn(List.of(mockAccount));
        mockBalance.setAvailableBalance(new BigDecimal("100.0000"));
        when(balanceRepository.findByAccount_AccountId(10L)).thenReturn(Optional.of(mockBalance));

        ShareCapitalTopUpRequest req = ShareCapitalTopUpRequest.builder()
                .amount(new BigDecimal("5000.00"))
                .build();

        assertThrows(InsufficientAccountFundsException.class, () ->
                shareCapitalService.topUpShareCapital(1001L, req)
        );
        verify(memberShareCapitalRepository, never()).save(any());
    }

    @Test
    @DisplayName("getDividendHistory: returns historical dividend payouts")
    void testGetDividendHistory() {
        when(memberShareCapitalRepository.findByCustomerId(1001L)).thenReturn(Optional.of(mockShareCapital));

        PatronageDividendPayout p2025 = PatronageDividendPayout.builder()
                .dividendId(1L)
                .memberShareCapital(mockShareCapital)
                .customerId(1001L)
                .fiscalYear(2025)
                .shareCapitalDividend(new BigDecimal("1812.5000"))
                .patronageRefund(new BigDecimal("940.0000"))
                .totalPayout(new BigDecimal("2752.5000"))
                .payoutDate(LocalDateTime.now())
                .referenceNo("DIV-2025-001")
                .status("PAID")
                .build();

        when(patronageDividendPayoutRepository.findByCustomerIdOrderByFiscalYearDesc(1001L))
                .thenReturn(List.of(p2025));

        List<DividendPayoutResponse> history = shareCapitalService.getDividendHistory(1001L);

        assertEquals(1, history.size());
        assertEquals(2025, history.get(0).getFiscalYear());
        assertEquals(new BigDecimal("2752.5000"), history.get(0).getTotalPayout());
    }
}
