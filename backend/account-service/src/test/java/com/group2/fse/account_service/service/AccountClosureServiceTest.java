package com.group2.fse.account_service.service;

import com.group2.fse.account_service.dto.AccountClosureRequestDto;
import com.group2.fse.account_service.dto.ClosureRequestResponse;
import com.group2.fse.account_service.dto.PageResponse;
import com.group2.fse.account_service.entity.Account;
import com.group2.fse.account_service.entity.AccountClosureRequest;
import com.group2.fse.account_service.entity.Balance;
import com.group2.fse.account_service.event.AccountClosureCompletedEvent;
import com.group2.fse.account_service.event.AccountEventPublisher;
import com.group2.fse.account_service.exception.*;
import com.group2.fse.account_service.repository.AccountClosureRequestRepository;
import com.group2.fse.account_service.repository.AccountFlagRepository;
import com.group2.fse.account_service.repository.AccountRepository;
import com.group2.fse.account_service.repository.BalanceRepository;
import com.group2.fse.account_service.service.impl.AccountClosureServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Closure Dual-Authorization & Customer Protection Service Tests")
class AccountClosureServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private AccountClosureRequestRepository closureRequestRepository;

    @Mock
    private AccountFlagRepository accountFlagRepository;

    @Mock
    private AccountEventPublisher accountEventPublisher;

    @InjectMocks
    private AccountClosureServiceImpl closureService;

    private Account mockAccount;
    private Balance zeroBalance;
    private Balance positiveBalance;

    @BeforeEach
    void setUp() {
        mockAccount = Account.builder()
                .accountId(4L)
                .customerId(101L)
                .accountNumber("ACC_10000004")
                .accountType("CHECKING")
                .currency("PHP")
                .status("ACTIVE")
                .build();

        zeroBalance = Balance.builder()
                .balanceId(1L)
                .account(mockAccount)
                .availableBalance(BigDecimal.ZERO.setScale(4))
                .build();

        positiveBalance = Balance.builder()
                .balanceId(2L)
                .account(mockAccount)
                .availableBalance(new BigDecimal("12500.0000"))
                .build();
    }

    @Test
    @DisplayName("[CLOSE-001] Should successfully stage account closure request when balance is 0.0000 PHP and no holds exist")
    void shouldStageClosureRequestSuccessfully() {
        AccountClosureRequestDto request = AccountClosureRequestDto.builder()
                .reason("No longer needed")
                .build();

        when(accountRepository.findById(4L)).thenReturn(Optional.of(mockAccount));
        when(closureRequestRepository.existsByAccount_AccountIdAndStatus(4L, "PENDING")).thenReturn(false);
        when(accountFlagRepository.existsByAccount_AccountIdAndStatus(4L, "ACTIVE")).thenReturn(false);
        when(balanceRepository.findByAccount_AccountId(4L)).thenReturn(Optional.of(zeroBalance));
        when(closureRequestRepository.save(any(AccountClosureRequest.class))).thenAnswer(i -> {
            AccountClosureRequest req = i.getArgument(0);
            req.setClosureRequestId(88L);
            return req;
        });

        ClosureRequestResponse response = closureService.submitClosureRequest(4L, 101L, request);

        assertNotNull(response);
        assertEquals(88L, response.getClosureRequestId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("ACC_10000004", response.getAccountNumber());
        assertEquals(new BigDecimal("0.0000"), response.getAvailableBalance());
        verify(closureRequestRepository).save(any(AccountClosureRequest.class));
    }

    @Test
    @DisplayName("[CLOSE-002] INVARIANT: Should REJECT closure request if account balance is > 0 (Anti-Expropriation Guard)")
    void shouldRejectClosureWhenBalanceIsPositive() {
        AccountClosureRequestDto request = AccountClosureRequestDto.builder()
                .reason("Closing account")
                .build();

        when(accountRepository.findById(4L)).thenReturn(Optional.of(mockAccount));
        when(closureRequestRepository.existsByAccount_AccountIdAndStatus(4L, "PENDING")).thenReturn(false);
        when(accountFlagRepository.existsByAccount_AccountIdAndStatus(4L, "ACTIVE")).thenReturn(false);
        when(balanceRepository.findByAccount_AccountId(4L)).thenReturn(Optional.of(positiveBalance));

        AccountNonZeroBalanceException ex = assertThrows(AccountNonZeroBalanceException.class, () ->
                closureService.submitClosureRequest(4L, 101L, request));

        assertTrue(ex.getMessage().contains("12500.0000"));
        verify(closureRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("[CLOSE-003] INVARIANT: Should REJECT closure request if account has ACTIVE risk or administrative hold")
    void shouldRejectClosureWhenActiveHoldExists() {
        AccountClosureRequestDto request = AccountClosureRequestDto.builder()
                .reason("Closing account")
                .build();

        when(accountRepository.findById(4L)).thenReturn(Optional.of(mockAccount));
        when(closureRequestRepository.existsByAccount_AccountIdAndStatus(4L, "PENDING")).thenReturn(false);
        when(accountFlagRepository.existsByAccount_AccountIdAndStatus(4L, "ACTIVE")).thenReturn(true);

        assertThrows(AccountActiveFlagException.class, () ->
                closureService.submitClosureRequest(4L, 101L, request));

        verify(closureRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("[CLOSE-004] INVARIANT: Should prevent duplicate PENDING closure requests on the same account")
    void shouldRejectDuplicatePendingClosureRequest() {
        AccountClosureRequestDto request = AccountClosureRequestDto.builder()
                .reason("Duplicate attempt")
                .build();

        when(accountRepository.findById(4L)).thenReturn(Optional.of(mockAccount));
        when(closureRequestRepository.existsByAccount_AccountIdAndStatus(4L, "PENDING")).thenReturn(true);

        assertThrows(DuplicateClosureRequestException.class, () ->
                closureService.submitClosureRequest(4L, 101L, request));

        verify(closureRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("[CLOSE-005] Security: Customer cannot request closure of another customer's account")
    void shouldRejectUnauthorizedCustomerClosureAttempt() {
        AccountClosureRequestDto request = AccountClosureRequestDto.builder()
                .reason("Unauthorized closure")
                .build();

        when(accountRepository.findById(4L)).thenReturn(Optional.of(mockAccount));

        assertThrows(UnauthorizedAccountAccessException.class, () ->
                closureService.submitClosureRequest(4L, 999L, request));

        verify(closureRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("[CLOSE-006] INVARIANT: Admin approves closure -> ACCOUNT transitions to CLOSED, but CUSTOMER remains ACTIVE")
    void shouldApproveClosureRequestAndKeepCustomerActive() {
        AccountClosureRequest closureReq = AccountClosureRequest.builder()
                .closureRequestId(88L)
                .account(mockAccount)
                .reason("Relocating")
                .status("PENDING")
                .requestedAt(LocalDateTime.now())
                .build();

        when(closureRequestRepository.findById(88L)).thenReturn(Optional.of(closureReq));
        when(balanceRepository.findByAccount_AccountId(4L)).thenReturn(Optional.of(zeroBalance));

        ClosureRequestResponse response = closureService.approveClosureRequest(88L, 1L);

        assertNotNull(response);
        assertEquals("CLOSED", response.getAccountStatus());
        assertEquals("ACTIVE", response.getCustomerStatus());
        assertEquals("CLOSED", mockAccount.getStatus());
        assertEquals("APPROVED", closureReq.getStatus());
        assertEquals(1L, closureReq.getApprovedBy());

        verify(accountRepository).save(mockAccount);
        verify(closureRequestRepository).save(closureReq);
        verify(accountEventPublisher).publishAccountClosure(any(AccountClosureCompletedEvent.class));
    }

    @Test
    @DisplayName("[CLOSE-007] Admin rejects closure request -> sets REJECTED status, account remains ACTIVE")
    void shouldRejectClosureRequestSuccessfully() {
        AccountClosureRequest closureReq = AccountClosureRequest.builder()
                .closureRequestId(88L)
                .account(mockAccount)
                .status("PENDING")
                .requestedAt(LocalDateTime.now())
                .build();

        when(closureRequestRepository.findById(88L)).thenReturn(Optional.of(closureReq));

        ClosureRequestResponse response = closureService.rejectClosureRequest(
                88L, 1L, "Pending uncleared check deposit discovered during reconciliation.");

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals("Pending uncleared check deposit discovered during reconciliation.", response.getRejectionReason());
        assertEquals("ACTIVE", mockAccount.getStatus());
        verify(closureRequestRepository).save(closureReq);
        verify(accountRepository, never()).save(any(Account.class));
        verify(accountEventPublisher, never()).publishAccountClosure(any());
    }

    @Test
    @DisplayName("[CLOSE-008] Should list pending closure requests with pagination")
    void shouldListPendingClosureRequests() {
        AccountClosureRequest closureReq = AccountClosureRequest.builder()
                .closureRequestId(88L)
                .account(mockAccount)
                .reason("Relocating")
                .status("PENDING")
                .requestedAt(LocalDateTime.now())
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<AccountClosureRequest> page = new PageImpl<>(List.of(closureReq), pageable, 1);
        when(closureRequestRepository.findByStatus("PENDING", pageable)).thenReturn(page);
        when(balanceRepository.findByAccount_AccountId(4L)).thenReturn(Optional.of(zeroBalance));

        PageResponse<ClosureRequestResponse> response = closureService.getPendingClosureRequests("PENDING", pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(88L, response.getContent().get(0).getClosureRequestId());
        assertEquals("PENDING", response.getContent().get(0).getStatus());
    }
}
