package com.group2.fse.account_service.service;

import com.group2.fse.account_service.dto.vault.CreateVaultRequest;
import com.group2.fse.account_service.dto.vault.VaultOperationRequest;
import com.group2.fse.account_service.dto.vault.VaultResponse;
import com.group2.fse.account_service.entity.Account;
import com.group2.fse.account_service.entity.AccountVault;
import com.group2.fse.account_service.entity.AccountVaultTransaction;
import com.group2.fse.account_service.entity.Balance;
import com.group2.fse.account_service.exception.InsufficientAccountFundsException;
import com.group2.fse.account_service.exception.InsufficientVaultFundsException;
import com.group2.fse.account_service.repository.AccountRepository;
import com.group2.fse.account_service.repository.AccountVaultRepository;
import com.group2.fse.account_service.repository.AccountVaultTransactionRepository;
import com.group2.fse.account_service.repository.BalanceRepository;
import com.group2.fse.account_service.service.impl.VaultServiceImpl;
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
@DisplayName("Virtual Ipon Vault Service Unit Tests")
class VaultServiceTest {

    @Mock
    private AccountVaultRepository vaultRepository;

    @Mock
    private AccountVaultTransactionRepository vaultTxRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private VaultServiceImpl vaultService;

    private Account mockAccount;
    private Balance mockBalance;
    private AccountVault mockVault;

    @BeforeEach
    void setUp() {
        mockAccount = Account.builder()
                .accountId(100L)
                .customerId(1001L)
                .accountNumber("ACC_10000001")
                .accountType("SAVINGS")
                .currency("PHP")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        mockBalance = Balance.builder()
                .balanceId(1L)
                .account(mockAccount)
                .availableBalance(new BigDecimal("50000.0000"))
                .updatedAt(LocalDateTime.now())
                .build();

        mockVault = AccountVault.builder()
                .vaultId(10L)
                .vaultReference("VLT_10000001")
                .customerId(1001L)
                .account(mockAccount)
                .name("Emergency Fund")
                .targetAmount(new BigDecimal("20000.0000"))
                .currentAmount(new BigDecimal("5000.0000"))
                .targetDate("31 Dec 2026")
                .category("Emergency")
                .iconCode(0xe59c)
                .colorValue(0xFF0D9488L)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("Should return customer vaults successfully")
    void getCustomerVaults_Success() {
        when(vaultRepository.findByCustomerIdAndStatusNotOrderByCreatedAtDesc(1001L, "CLOSED"))
                .thenReturn(List.of(mockVault));

        List<VaultResponse> vaults = vaultService.getCustomerVaults(1001L);

        assertNotNull(vaults);
        assertEquals(1, vaults.size());
        assertEquals("Emergency Fund", vaults.get(0).getName());
        assertEquals(1001L, vaults.get(0).getCustomerId());
        assertEquals(new BigDecimal("5000.0000"), vaults.get(0).getCurrentAmount());
    }

    @Test
    @DisplayName("Should create vault and earmark initial funds successfully")
    void createVault_WithInitialAmount_Success() {
        CreateVaultRequest request = CreateVaultRequest.builder()
                .accountId(100L)
                .name("Emergency Fund")
                .targetAmount(new BigDecimal("20000.00"))
                .initialAmount(new BigDecimal("5000.00"))
                .targetDate("31 Dec 2026")
                .category("Emergency")
                .iconCode(0xe59c)
                .colorValue(0xFF0D9488L)
                .build();

        when(accountRepository.findById(100L)).thenReturn(Optional.of(mockAccount));
        when(balanceRepository.findByAccount_AccountId(100L)).thenReturn(Optional.of(mockBalance));
        when(vaultRepository.save(any(AccountVault.class))).thenAnswer(invocation -> {
            AccountVault v = invocation.getArgument(0);
            v.setVaultId(10L);
            return v;
        });

        VaultResponse response = vaultService.createVault(1001L, request);

        assertNotNull(response);
        assertEquals("Emergency Fund", response.getName());
        assertEquals(new BigDecimal("5000.0000"), response.getCurrentAmount());
        assertEquals(new BigDecimal("45000.0000"), mockBalance.getAvailableBalance());
        verify(balanceRepository).save(mockBalance);
        verify(vaultTxRepository).save(any(AccountVaultTransaction.class));
    }

    @Test
    @DisplayName("Should allocate funds, update balance, and auto-complete vault when target reached")
    void allocateFunds_AutoCompletesWhenTargetReached() {
        VaultOperationRequest request = VaultOperationRequest.builder()
                .amount(new BigDecimal("15000.00"))
                .build();

        when(vaultRepository.findByVaultIdAndCustomerId(10L, 1001L)).thenReturn(Optional.of(mockVault));
        when(balanceRepository.findByAccount_AccountId(100L)).thenReturn(Optional.of(mockBalance));
        when(vaultRepository.save(any(AccountVault.class))).thenAnswer(inv -> inv.getArgument(0));

        VaultResponse response = vaultService.allocateFunds(1001L, 10L, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("20000.0000"), response.getCurrentAmount());
        assertEquals("COMPLETED", response.getStatus());
        assertTrue(Boolean.TRUE.equals(response.getIsCompleted()));
        assertEquals(new BigDecimal("35000.0000"), mockBalance.getAvailableBalance());
        verify(balanceRepository).save(mockBalance);
        verify(vaultTxRepository).save(any(AccountVaultTransaction.class));
    }

    @Test
    @DisplayName("Should throw InsufficientAccountFundsException when allocating more than available balance")
    void allocateFunds_InsufficientAvailableBalance_ThrowsException() {
        VaultOperationRequest request = VaultOperationRequest.builder()
                .amount(new BigDecimal("60000.00"))
                .build();

        when(vaultRepository.findByVaultIdAndCustomerId(10L, 1001L)).thenReturn(Optional.of(mockVault));
        when(balanceRepository.findByAccount_AccountId(100L)).thenReturn(Optional.of(mockBalance));

        assertThrows(InsufficientAccountFundsException.class, () ->
                vaultService.allocateFunds(1001L, 10L, request)
        );
        verify(vaultRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should release funds and restore available balance")
    void releaseFunds_Success() {
        VaultOperationRequest request = VaultOperationRequest.builder()
                .amount(new BigDecimal("2000.00"))
                .build();

        when(vaultRepository.findByVaultIdAndCustomerId(10L, 1001L)).thenReturn(Optional.of(mockVault));
        when(balanceRepository.findByAccount_AccountId(100L)).thenReturn(Optional.of(mockBalance));
        when(vaultRepository.save(any(AccountVault.class))).thenAnswer(inv -> inv.getArgument(0));

        VaultResponse response = vaultService.releaseFunds(1001L, 10L, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("3000.0000"), response.getCurrentAmount());
        assertEquals(new BigDecimal("52000.0000"), mockBalance.getAvailableBalance());
        verify(balanceRepository).save(mockBalance);
        verify(vaultTxRepository).save(any(AccountVaultTransaction.class));
    }

    @Test
    @DisplayName("Should throw InsufficientVaultFundsException when releasing more than vault current amount")
    void releaseFunds_InsufficientVaultFunds_ThrowsException() {
        VaultOperationRequest request = VaultOperationRequest.builder()
                .amount(new BigDecimal("10000.00"))
                .build();

        when(vaultRepository.findByVaultIdAndCustomerId(10L, 1001L)).thenReturn(Optional.of(mockVault));

        assertThrows(InsufficientVaultFundsException.class, () ->
                vaultService.releaseFunds(1001L, 10L, request)
        );
        verify(balanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should close vault, restore remaining earmarked funds to balance, and set status to CLOSED")
    void closeVault_RestoresFundsAndCloses() {
        when(vaultRepository.findByVaultIdAndCustomerId(10L, 1001L)).thenReturn(Optional.of(mockVault));
        when(balanceRepository.findByAccount_AccountId(100L)).thenReturn(Optional.of(mockBalance));
        when(vaultRepository.save(any(AccountVault.class))).thenAnswer(inv -> inv.getArgument(0));

        VaultResponse response = vaultService.closeVault(1001L, 10L);

        assertNotNull(response);
        assertEquals("CLOSED", response.getStatus());
        assertEquals(new BigDecimal("0.0000"), response.getCurrentAmount());
        assertEquals(new BigDecimal("55000.0000"), mockBalance.getAvailableBalance());
        verify(balanceRepository).save(mockBalance);
        verify(vaultTxRepository).save(any(AccountVaultTransaction.class));
    }
}
