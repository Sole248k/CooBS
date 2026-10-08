package com.group2.fse.customer_service.service;

import com.group2.fse.customer_service.client.AccountClient;
import com.group2.fse.customer_service.client.TransactionClient;
import com.group2.fse.customer_service.dto.BeneficiaryDto;
import com.group2.fse.customer_service.dto.Customer360Response;
import com.group2.fse.customer_service.dto.Customer360Response.AccountDto;
import com.group2.fse.customer_service.dto.Customer360Response.SpendingBreakdown;
import com.group2.fse.customer_service.dto.Customer360Response.SpendingItemDto;
import com.group2.fse.customer_service.dto.Customer360Response.TransactionSummaryDto;
import com.group2.fse.customer_service.entity.ChannelProfile;
import com.group2.fse.customer_service.exception.CustomerNotFoundException;
import com.group2.fse.customer_service.repository.ChannelProfileRepository;
import com.group2.fse.customer_service.service.impl.Customer360ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Customer360ServiceImplTest {

    @Mock
    private ChannelProfileRepository channelProfileRepository;

    @Mock
    private BeneficiaryService beneficiaryService;

    @Mock
    private AccountClient accountClient;

    @Mock
    private TransactionClient transactionClient;

    @InjectMocks
    private Customer360ServiceImpl customer360Service;

    private ChannelProfile sampleProfile;

    @BeforeEach
    void setUp() {
        sampleProfile = ChannelProfile.builder()
                .customerId(1L)
                .displayName("Juan Dela Cruz")
                .membershipNumber("MBR-000001")
                .kycStatus("VERIFIED")
                .kycTier("TIER_3_FULL")
                .creditScore(780)
                .riskScore(new BigDecimal("0.12"))
                .build();
    }

    @Test
    @DisplayName("getCustomer360 aggregates profile, beneficiaries, accounts, recent transactions, and spending breakdown")
    void getCustomer360_success() {
        when(channelProfileRepository.findById(1L)).thenReturn(Optional.of(sampleProfile));
        when(beneficiaryService.getBeneficiaries(1L)).thenReturn(List.of(
                BeneficiaryDto.builder().beneficiaryId(10L).nickname("Maria").accountNumber("ACC_10000002").bankCode("COOBS").build()
        ));

        List<AccountDto> accounts = List.of(
                AccountDto.builder()
                        .accountId(1L)
                        .accountNumber("ACC_10000001")
                        .accountType("SAVINGS")
                        .currency("PHP")
                        .status("ACTIVE")
                        .availableBalance("15000.00")
                        .build(),
                AccountDto.builder()
                        .accountId(2L)
                        .accountNumber("LN_10000002")
                        .accountType("LOAN")
                        .currency("PHP")
                        .status("ACTIVE")
                        .availableBalance("50000.00")
                        .build()
        );
        when(accountClient.fetchCustomerAccounts(1L)).thenReturn(accounts);

        List<TransactionSummaryDto> mockTransactions = List.of(
                TransactionSummaryDto.builder()
                        .referenceNo("TXN-1001")
                        .direction("OUT")
                        .amount("1500.00")
                        .category("UTILITIES")
                        .build()
        );
        when(transactionClient.fetchRecentTransactions(1L, 5)).thenReturn(mockTransactions);

        SpendingBreakdown mockBreakdown = SpendingBreakdown.builder()
                .periodDays(30)
                .items(List.of(
                        SpendingItemDto.builder().category("UTILITIES").amount("1500.00").count(1).build()
                ))
                .build();
        when(transactionClient.fetchSpendingBreakdown(1L, 30)).thenReturn(mockBreakdown);

        // Member view (isStaff = false) -> risk score masked
        Customer360Response customerView = customer360Service.getCustomer360(1L, false);

        assertThat(customerView.getCustomerId()).isEqualTo(1L);
        assertThat(customerView.getDisplayName()).isEqualTo("Juan Dela Cruz");
        assertThat(customerView.getMembershipNumber()).isEqualTo("MBR-000001");
        assertThat(customerView.getKycStatus()).isEqualTo("VERIFIED");
        assertThat(customerView.getKycTier()).isEqualTo("TIER_3_FULL");
        assertThat(customerView.getCreditScore()).isEqualTo(780);
        assertThat(customerView.getRiskScore()).isNull();
        assertThat(customerView.getBeneficiaries()).hasSize(1);
        assertThat(customerView.getAccounts()).hasSize(2);
        assertThat(customerView.getTotals().getDeposits()).isEqualTo("15000.00");
        assertThat(customerView.getTotals().getLoans()).isEqualTo("50000.00");
        assertThat(customerView.getRecentTransactions()).hasSize(1);
        assertThat(customerView.getRecentTransactions().get(0).getReferenceNo()).isEqualTo("TXN-1001");
        assertThat(customerView.getSpendingBreakdown().getItems()).hasSize(1);
        assertThat(customerView.getSpendingBreakdown().getItems().get(0).getCategory()).isEqualTo("UTILITIES");

        // Staff view (isStaff = true) -> risk score visible
        Customer360Response staffView = customer360Service.getCustomer360(1L, true);
        assertThat(staffView.getRiskScore()).isEqualTo("0.12");

        verify(transactionClient, times(2)).fetchRecentTransactions(1L, 5);
        verify(transactionClient, times(2)).fetchSpendingBreakdown(1L, 30);
    }

    @Test
    @DisplayName("getCustomer360 throws CustomerNotFoundException if channel profile missing")
    void getCustomer360_notFound() {
        when(channelProfileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customer360Service.getCustomer360(999L, false))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("999");
    }
}
