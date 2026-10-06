package com.group2.fse.customer_service.service.impl;

import com.group2.fse.customer_service.dto.*;
import com.group2.fse.customer_service.entity.ChannelProfile;
import com.group2.fse.customer_service.exception.CustomerNotFoundException;
import com.group2.fse.customer_service.repository.ChannelProfileRepository;
import com.group2.fse.customer_service.service.BeneficiaryService;
import com.group2.fse.customer_service.service.Customer360Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class Customer360ServiceImpl implements Customer360Service {

    private final ChannelProfileRepository channelProfileRepository;
    private final BeneficiaryService beneficiaryService;

    @Value("${app.clients.account.base-url:http://localhost:8082}")
    private String accountServiceBaseUrl;

    @Value("${app.security.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String jwtSecret;

    @Override
    @Transactional(readOnly = true)
    public Customer360Response getCustomer360(Long customerId, boolean isStaff) {
        ChannelProfile profile = channelProfileRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Channel profile not found for customer ID: " + customerId));

        List<BeneficiaryDto> beneficiaries = beneficiaryService.getBeneficiaries(customerId);

        List<Customer360Response.AccountDto> accounts = fetchAccounts(customerId);

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
                .recentTransactions(new ArrayList<>())
                .spendingBreakdown(new Customer360Response.SpendingBreakdown(30, new ArrayList<>()))
                .beneficiaries(beneficiaries)
                .asOf(Instant.now().toString())
                .build();
    }

    private List<Customer360Response.AccountDto> fetchAccounts(Long customerId) {
        List<Customer360Response.AccountDto> list = new ArrayList<>();
        try {
            RestTemplate restTemplate = new RestTemplate();

            String url = accountServiceBaseUrl + "/internal/v1/customers/" + customerId + "/accounts";
            log.info("Fetching accounts for customer 360 from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                for (Map<String, Object> map : response.getBody()) {
                    Long accId = map.get("accountId") != null ? Long.valueOf(map.get("accountId").toString()) : null;
                    String accNum = map.get("accountNumber") != null ? map.get("accountNumber").toString() : "";
                    String accType = map.get("accountType") != null ? map.get("accountType").toString() : "";
                    String curr = map.get("currency") != null ? map.get("currency").toString() : "PHP";
                    String stat = map.get("status") != null ? map.get("status").toString() : "ACTIVE";
                    String availBal = map.get("availableBalance") != null ? map.get("availableBalance").toString() : "0.00";

                    list.add(Customer360Response.AccountDto.builder()
                            .accountId(accId)
                            .accountNumber(accNum)
                            .accountType(accType)
                            .currency(curr)
                            .status(stat)
                            .availableBalance(availBal)
                            .ledgerBalance(availBal)
                            .heldAmount("0.00")
                            .build());
                }
            }
        } catch (Exception ex) {
            log.warn("Could not retrieve accounts for customer {} from account-service: {}", customerId, ex.getMessage());
        }
        return list;
    }
}
