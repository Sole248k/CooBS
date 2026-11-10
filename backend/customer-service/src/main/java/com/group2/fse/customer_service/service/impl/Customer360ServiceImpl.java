package com.group2.fse.customer_service.service.impl;

import com.group2.fse.customer_service.client.AccountClient;
import com.group2.fse.customer_service.client.TransactionClient;
import com.group2.fse.customer_service.dto.*;
import com.group2.fse.customer_service.entity.ChannelProfile;
import com.group2.fse.customer_service.exception.CustomerNotFoundException;
import com.group2.fse.customer_service.repository.ChannelProfileRepository;
import com.group2.fse.customer_service.service.BeneficiaryService;
import com.group2.fse.customer_service.service.Customer360Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class Customer360ServiceImpl implements Customer360Service {

    private final ChannelProfileRepository channelProfileRepository;
    private final BeneficiaryService beneficiaryService;
    private final AccountClient accountClient;
    private final TransactionClient transactionClient;

    @Override
    @Transactional(readOnly = true)
    public Customer360Response getCustomer360(Long customerId, boolean isStaff) {
        ChannelProfile profile = channelProfileRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Channel profile not found for customer ID: " + customerId));

        List<BeneficiaryDto> beneficiaries = beneficiaryService.getBeneficiaries(customerId);

        List<Customer360Response.AccountDto> accounts = accountClient.fetchCustomerAccounts(customerId);

        List<Customer360Response.TransactionSummaryDto> recentTransactions = transactionClient.fetchRecentTransactions(customerId, 25);
        Customer360Response.SpendingBreakdown spendingBreakdown = transactionClient.fetchSpendingBreakdown(customerId, 30);

        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalLoans = BigDecimal.ZERO;

        for (Customer360Response.AccountDto acc : accounts) {
            BigDecimal bal = BigDecimal.ZERO;
            try {
                if (acc.getAvailableBalance() != null) {
                    bal = new BigDecimal(acc.getAvailableBalance());
                }
            } catch (Exception ignored) {}

            if ("LOAN".equalsIgnoreCase(acc.getAccountType())) {
                totalLoans = totalLoans.add(bal);
            } else {
                totalDeposits = totalDeposits.add(bal);
            }
        }

        String riskScore = isStaff && profile.getRiskScore() != null ? profile.getRiskScore().toString() : null;

        return Customer360Response.builder()
                .customerId(profile.getCustomerId())
                .displayName(profile.getDisplayName())
                .membershipNumber(profile.getMembershipNumber())
                .kycStatus(profile.getKycStatus())
                .kycTier(profile.getKycTier())
                .creditScore(profile.getCreditScore())
                .riskScore(riskScore)
                .accounts(accounts)
                .totals(new Customer360Response.FinancialTotals(totalDeposits.setScale(2).toString(), totalLoans.setScale(2).toString()))
                .recentTransactions(recentTransactions)
                .spendingBreakdown(spendingBreakdown)
                .beneficiaries(beneficiaries)
                .asOf(Instant.now().toString())
                .build();
    }
}
