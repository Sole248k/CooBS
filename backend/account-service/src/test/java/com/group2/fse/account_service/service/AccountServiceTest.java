package com.group2.fse.account_service.service;

import com.group2.fse.account_service.dto.AccountResponse;
import com.group2.fse.account_service.dto.CreateAccountRequest;
import com.group2.fse.account_service.entity.Account;
import com.group2.fse.account_service.entity.Balance;
import com.group2.fse.account_service.entity.Customer;
import com.group2.fse.account_service.entity.Kyc;
import com.group2.fse.account_service.event.AccountCreatedEvent;
import com.group2.fse.account_service.event.AccountEventPublisher;
import com.group2.fse.account_service.exception.AccountNotFoundException;
import com.group2.fse.account_service.exception.InvalidKycStateException;
import com.group2.fse.account_service.exception.UnauthorizedAccountAccessException;
import com.group2.fse.account_service.repository.AccountRepository;
import com.group2.fse.account_service.repository.BalanceRepository;
import com.group2.fse.account_service.repository.CustomerRepository;
import com.group2.fse.account_service.service.impl.AccountServiceImpl;
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
@DisplayName("Deposit Account Lifecycle Service Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AccountEventPublisher accountEventPublisher;

    @InjectMocks
    private AccountServiceImpl accountService;

    private Customer verifiedCustomer;
    private Customer pendingCustomer;
    private Account mockAccount;
    private Balance mockBalance;

    @BeforeEach
    void setUp() {
        Kyc mockKyc = Kyc.builder().firstName("John").lastName("Doe").status("VERIFIED").build();
        verifiedCustomer = Customer.builder()
                .customerId(101L)
                .kyc(mockKyc)
                .kycStatus("VERIFIED")
                .build();
        mockKyc.setCustomer(verifiedCustomer);

        pendingCustomer = Customer.builder()
                .customerId(102L)
                .kycStatus("PENDING")
                .build();

        mockAccount = Account.builder()
                .accountId(305L)
                .customer(verifiedCustomer)
                .accountNumber("ACC_10000005")
                .accountType("SAVINGS")
                .currency("PHP")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        mockBalance = Balance.builder()
                .balanceId(1L)
                .account(mockAccount)
                .availableBalance(BigDecimal.ZERO.setScale(4))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("[ACC-001] Should successfully create new deposit account with 0.0000 PHP balance for verified customer")
    void shouldCreateAccountSuccessfullyForVerifiedCustomer() {
        when(customerRepository.findById(101L)).thenReturn(Optional.of(verifiedCustomer));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> {
            Account acc = i.getArgument(0);
            acc.setAccountId(305L);
            return acc;
        });
        when(balanceRepository.save(any(Balance.class))).thenAnswer(i -> {
            Balance b = i.getArgument(0);
            b.setBalanceId(1L);
            return b;
        });

        CreateAccountRequest request = CreateAccountRequest.builder()
                .customerId(101L)
                .accountType("SAVINGS")
                .currency("PHP")
                .build();

        AccountResponse response = accountService.createAccount(request);

        assertNotNull(response);
        assertEquals(305L, response.getAccountId());
        assertEquals("SAVINGS", response.getAccountType());
        assertEquals("PHP", response.getCurrency());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(new BigDecimal("0.0000"), response.getAvailableBalance());
        assertTrue(response.getAccountNumber().startsWith("ACC_"));

        verify(accountRepository).save(any(Account.class));
        verify(balanceRepository).save(any(Balance.class));
        verify(accountEventPublisher).publishAccountCreated(any(AccountCreatedEvent.class));
    }

    @Test
    @DisplayName("[ACC-002] Should reject account opening if customer KYC status is not VERIFIED")
    void shouldRejectAccountOpeningWhenKycNotVerified() {
        when(customerRepository.findById(102L)).thenReturn(Optional.of(pendingCustomer));

        CreateAccountRequest request = CreateAccountRequest.builder()
                .customerId(102L)
                .accountType("SAVINGS")
                .currency("PHP")
                .build();

        assertThrows(InvalidKycStateException.class, () -> accountService.createAccount(request));
        verify(accountRepository, never()).save(any(Account.class));
        verify(balanceRepository, never()).save(any(Balance.class));
    }

    @Test
    @DisplayName("[ACC-003] Should list all deposit accounts owned by customer with balances")
    void shouldGetCustomerAccountsSuccessfully() {
        when(customerRepository.existsById(101L)).thenReturn(true);
        when(accountRepository.findByCustomer_CustomerId(101L)).thenReturn(List.of(mockAccount));
        when(balanceRepository.findByAccount_AccountId(305L)).thenReturn(Optional.of(mockBalance));

        List<AccountResponse> accounts = accountService.getCustomerAccounts(101L);

        assertNotNull(accounts);
        assertEquals(1, accounts.size());
        assertEquals("ACC_10000005", accounts.get(0).getAccountNumber());
        assertEquals(new BigDecimal("0.0000"), accounts.get(0).getAvailableBalance());
    }

    @Test
    @DisplayName("[ACC-004] Should allow customer to access their own account metadata")
    void shouldAllowCustomerToGetOwnAccount() {
        when(accountRepository.findById(305L)).thenReturn(Optional.of(mockAccount));
        when(balanceRepository.findByAccount_AccountId(305L)).thenReturn(Optional.of(mockBalance));

        AccountResponse response = accountService.getAccountById(305L, 101L, false);

        assertNotNull(response);
        assertEquals(305L, response.getAccountId());
    }

    @Test
    @DisplayName("[ACC-005] Should reject customer attempting to access another customer's account")
    void shouldRejectUnauthorizedCustomerAccess() {
        when(accountRepository.findById(305L)).thenReturn(Optional.of(mockAccount));

        // Customer ID 999 attempts to access account belonging to customer ID 101
        assertThrows(UnauthorizedAccountAccessException.class, () ->
                accountService.getAccountById(305L, 999L, false));
    }

    @Test
    @DisplayName("[ACC-006] Should allow staff / teller to inspect any account metadata")
    void shouldAllowStaffAccessToAnyAccount() {
        when(accountRepository.findById(305L)).thenReturn(Optional.of(mockAccount));
        when(balanceRepository.findByAccount_AccountId(305L)).thenReturn(Optional.of(mockBalance));

        // Teller (isStaff = true) with user ID 2 accessing customer 101's account
        AccountResponse response = accountService.getAccountById(305L, 2L, true);

        assertNotNull(response);
        assertEquals(305L, response.getAccountId());
    }

    @Test
    @DisplayName("[ACC-007] Should update account status directly (e.g. FROZEN)")
    void shouldUpdateAccountStatusSuccessfully() {
        when(accountRepository.findById(305L)).thenReturn(Optional.of(mockAccount));
        when(accountRepository.save(mockAccount)).thenReturn(mockAccount);
        when(balanceRepository.findByAccount_AccountId(305L)).thenReturn(Optional.of(mockBalance));

        AccountResponse response = accountService.updateAccountStatus(305L, "FROZEN");

        assertNotNull(response);
        assertEquals("FROZEN", mockAccount.getStatus());
        verify(accountRepository).save(mockAccount);
    }
}
