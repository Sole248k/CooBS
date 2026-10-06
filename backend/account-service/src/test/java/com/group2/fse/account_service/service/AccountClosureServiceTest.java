package com.group2.fse.account_service.service;

import com.group2.fse.account_service.dto.AccountClosureRequestDto;
import com.group2.fse.account_service.dto.ClosureRequestResponse;
import com.group2.fse.account_service.dto.PageResponse;
import com.group2.fse.account_service.entity.*;
import com.group2.fse.account_service.event.AccountClosureCompletedEvent;
import com.group2.fse.account_service.event.AccountEventPublisher;
import com.group2.fse.account_service.exception.*;
import com.group2.fse.account_service.repository.*;
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
    private UserRepository userRepository;

    @Mock
    private AccountEventPublisher accountEventPublisher;

    @InjectMocks
    private AccountClosureServiceImpl closureService;

    private Customer mockCustomer;
    private Account mockAccount;
    private Balance zeroBalance;
    private Balance positiveBalance;

    @BeforeEach
    void setUp() {
        Kyc mockKyc = Kyc.builder()
                .firstName("John")
                .lastName("Doe")
                .status("VERIFIED")
                .build();
        mockCustomer = Customer.builder()
                .customerId(101L)
                .kyc(mockKyc)
                .kycStatus("VERIFIED")
                .build();
        mockKyc.setCustomer(mockCustomer);

        mockAccount = Account.builder()
                .accountId(4L)
                .customer(mockCustomer)
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
        when(accountRepository.findById(4L)).thenReturn(Optional.of(mockAccount));
        when(closureRequestRepository.existsByAccount_AccountIdAndStatus(4L, "PENDING")).thenReturn(false);
        when(accountFlagRepository.existsByAccount_AccountIdAndStatus(4L, "ACTIVE")).thenReturn(false);
        when(balanceRepository.findByAccount_AccountId(4L)).thenReturn(Optional.of(zeroBalance));
        when(closureRequestRepository.save(any(AccountClosureRequest.class))).thenAnswer(i -> {
            AccountClosureRequest r = i.getArgument(0);
            r.setClosureRequestId(88L);
            return r;
        });

        AccountClosureRequestDto request = new AccountClosureRequestDto("Relocating overseas, no longer using this account.");

        ClosureRequestResponse response = closureService.submitClosureRequest(4L, 101L, request);

        assertNotNull(response);
        assertEquals(88L, response.getClosureRequestId());
        assertEquals("PENDING", response.getStatus());
        assertEquals(new BigDecimal("0.0000"), response.getAvailableBalance());
        verify(closureRequestRepository).save(any(AccountClosureRequest.class));
    }

    @Test
    @DisplayName("[CLOSE-002] INVARIANT: Should strictly reject closure request when account has non-zero balance")
    void shouldRejectClosureRequestWhenBalanceIsNonZero() {
        when(accountRepository.findById(4L)).thenReturn(Optional.of(mockAccount));
        when(closureRequestRepository.existsByAccount_AccountIdAndStatus(4L, "PENDING")).thenReturn(false);
        when(accountFlagRepository.existsByAccount_AccountIdAndStatus(4L, "ACTIVE")).thenReturn(false);
        when(balanceRepository.findByAccount_AccountId(4L)).thenReturn(Optional.of(positiveBalance));

        AccountClosureRequestDto request = new AccountClosureRequestDto("Trying to close with funds");

        AccountNonZeroBalanceException ex = assertThrows(AccountNonZeroBalanceException.class, () ->
                closureService.submitClosureRequest(4L, 101L, request));

        assertEquals("ACC_10000004", ex.getAccountNumber());
        assertEquals(new BigDecimal("12500.0000"), ex.getBalance());
        verify(closureRequestRepository, never()).save(any(AccountClosureRequest.class));
    }

    @Test
    @DisplayName("[CLOSE-003] INVARIANT: Should strictly reject closure request when active administrative hold exists")
    void shouldRejectClosureRequestWhenActiveHoldExists() {
        when(accountRepository.findById(4L)).thenReturn(Optional.of(mockAccount));
        when(closureRequestRepository.existsByAccount_AccountIdAndStatus(4L, "PENDING")).thenReturn(false);
        when(accountFlagRepository.existsByAccount_AccountIdAndStatus(4L, "ACTIVE")).thenReturn(true);

        AccountClosureRequestDto request = new AccountClosureRequestDto("Trying to close flagged account");

        assertThrows(AccountActiveFlagException.class, () ->
                closureService.submitClosureRequest(4L, 101L, request));
        verify(closureRequestRepository, never()).save(any(AccountClosureRequest.class));
    }

    @Test
    @DisplayName("[CLOSE-004] INVARIANT: Should reject closure request if submitted by non-owner")
    void shouldRejectClosureRequestWhenNotOwner() {
        when(accountRepository.findById(4L)).thenReturn(Optional.of(mockAccount));

        // Customer ID 999 attempts to close account belonging to customer ID 101
        AccountClosureRequestDto request = new AccountClosureRequestDto("Not my account");

        assertThrows(UnauthorizedAccountAccessException.class, () ->
                closureService.submitClosureRequest(4L, 999L, request));
        verify(closureRequestRepository, never()).save(any(AccountClosureRequest.class));
    }

    @Test
    @DisplayName("[CLOSE-005] INVARIANT: Should reject duplicate closure request if one is already PENDING")
    void shouldRejectDuplicatePendingClosureRequest() {
        when(accountRepository.findById(4L)).thenReturn(Optional.of(mockAccount));
        when(closureRequestRepository.existsByAccount_AccountIdAndStatus(4L, "PENDING")).thenReturn(true);

        AccountClosureRequestDto request = new AccountClosureRequestDto("Another request");

        assertThrows(DuplicateClosureRequestException.class, () ->
                closureService.submitClosureRequest(4L, 101L, request));
        verify(closureRequestRepository, never()).save(any(AccountClosureRequest.class));
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

        User adminUser = User.builder().userId(1L).username("admin_boss").build();

        when(closureRequestRepository.findById(88L)).thenReturn(Optional.of(closureReq));
        when(balanceRepository.findByAccount_AccountId(4L)).thenReturn(Optional.of(zeroBalance));
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        ClosureRequestResponse response = closureService.approveClosureRequest(88L, 1L);

        assertNotNull(response);
        assertEquals("CLOSED", response.getAccountStatus());
        assertEquals("ACTIVE", response.getCustomerStatus());
        assertEquals("CLOSED", mockAccount.getStatus());
        assertEquals("VERIFIED", mockCustomer.getKycStatus()); // Customer remains active and verified!
        assertEquals("APPROVED", closureReq.getStatus());
        assertEquals(adminUser, closureReq.getApprovedBy());

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

        User adminUser = User.builder().userId(1L).username("admin_boss").build();
        when(closureRequestRepository.findById(88L)).thenReturn(Optional.of(closureReq));
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        ClosureRequestResponse response = closureService.rejectClosureRequest(
                88L, 1L, "Pending uncleared check deposit discovered during reconciliation.");

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals("Pending uncleared check deposit discovered during reconciliation.", response.getRejectionReason());
        assertEquals("ACTIVE", mockAccount.getStatus()); // Account remains ACTIVE
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
