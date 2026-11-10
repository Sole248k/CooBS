package com.group2.fse.account_service.service;

import com.group2.fse.account_service.dto.AccountFlagRequest;
import com.group2.fse.account_service.dto.AccountFlagResponse;
import com.group2.fse.account_service.entity.Account;
import com.group2.fse.account_service.entity.AccountFlag;
import com.group2.fse.account_service.exception.AccountNotFoundException;
import com.group2.fse.account_service.repository.AccountFlagRepository;
import com.group2.fse.account_service.repository.AccountRepository;
import com.group2.fse.account_service.service.impl.AccountFlagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Administrative & Risk Holds Service Tests")
class AccountFlagServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountFlagRepository accountFlagRepository;

    @InjectMocks
    private AccountFlagServiceImpl accountFlagService;

    private Account mockAccount;

    @BeforeEach
    void setUp() {
        mockAccount = Account.builder()
                .accountId(1L)
                .customerId(101L)
                .accountNumber("ACC_10000001")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("[FLAG-001] Should successfully impose administrative hold on account")
    void shouldAddFlagSuccessfully() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(mockAccount));
        when(accountFlagRepository.save(any(AccountFlag.class))).thenAnswer(i -> {
            AccountFlag f = i.getArgument(0);
            f.setFlagId(12L);
            return f;
        });

        AccountFlagRequest request = new AccountFlagRequest("Subpoena / Court order freeze request (Reference: SEC-2026-992)");

        AccountFlagResponse response = accountFlagService.addFlag(1L, request, 2L);

        assertNotNull(response);
        assertEquals(12L, response.getFlagId());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals("Subpoena / Court order freeze request (Reference: SEC-2026-992)", response.getReason());
        assertEquals(2L, response.getFlaggedBy());
        verify(accountFlagRepository).save(any(AccountFlag.class));
    }

    @Test
    @DisplayName("[FLAG-002] Should throw AccountNotFoundException when adding flag to non-existent account")
    void shouldThrowExceptionWhenAccountNotFoundForFlag() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        AccountFlagRequest request = new AccountFlagRequest("Risk hold");

        assertThrows(AccountNotFoundException.class, () ->
                accountFlagService.addFlag(999L, request, 2L));
        verify(accountFlagRepository, never()).save(any());
    }

    @Test
    @DisplayName("[FLAG-003] Should formally lift administrative hold with removedBy and removedAt metadata")
    void shouldRemoveFlagSuccessfully() {
        AccountFlag flag = AccountFlag.builder()
                .flagId(12L)
                .account(mockAccount)
                .reason("Court order")
                .status("ACTIVE")
                .flaggedBy(2L)
                .flaggedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(mockAccount));
        when(accountFlagRepository.findById(12L)).thenReturn(Optional.of(flag));

        AccountFlagResponse response = accountFlagService.removeFlag(1L, 12L, 1L);

        assertNotNull(response);
        assertEquals("REMOVED", response.getStatus());
        assertEquals(1L, response.getRemovedBy());
        assertNotNull(response.getRemovedAt());
        assertEquals("Administrative hold lifted successfully.", response.getMessage());
        verify(accountFlagRepository).save(flag);
    }

    @Test
    @DisplayName("[FLAG-004] Should list all flags for an account")
    void shouldGetFlagsForAccount() {
        AccountFlag flag = AccountFlag.builder()
                .flagId(12L)
                .account(mockAccount)
                .reason("Court order")
                .status("ACTIVE")
                .flaggedBy(2L)
                .flaggedAt(LocalDateTime.now())
                .build();

        when(accountRepository.existsById(1L)).thenReturn(true);
        when(accountFlagRepository.findByAccount_AccountId(1L)).thenReturn(List.of(flag));

        List<AccountFlagResponse> flags = accountFlagService.getFlagsForAccount(1L);

        assertNotNull(flags);
        assertEquals(1, flags.size());
        assertEquals(12L, flags.get(0).getFlagId());
        assertEquals("ACTIVE", flags.get(0).getStatus());
    }
}
