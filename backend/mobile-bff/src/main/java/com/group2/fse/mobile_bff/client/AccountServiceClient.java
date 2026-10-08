package com.group2.fse.mobile_bff.client;

import com.group2.fse.mobile_bff.dto.MobileAccountSummary;
import com.group2.fse.mobile_bff.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class AccountServiceClient {

    private final RestTemplate restTemplate;
    private final String accountServiceBaseUrl;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public AccountServiceClient(
            RestTemplate restTemplate,
            @Value("${app.clients.account.base-url:http://localhost:8082}") String accountServiceBaseUrl,
            JwtTokenProvider jwtTokenProvider) {
        this.restTemplate = restTemplate;
        this.accountServiceBaseUrl = accountServiceBaseUrl;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public List<MobileAccountSummary> fetchCustomerAccounts(Long customerId, String correlationId) {
        List<MobileAccountSummary> list = new ArrayList<>();
        try {
            String url = org.springframework.web.util.UriComponentsBuilder.fromUriString(accountServiceBaseUrl)
                    .pathSegment("internal", "v1", "customers", String.valueOf(customerId), "accounts")
                    .build().encode().toUriString();
            log.debug("Fetching customer accounts from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtTokenProvider.generateServiceToken());
            if (correlationId != null && !correlationId.isBlank()) {
                headers.set("X-Correlation-Id", correlationId);
            }

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
                    String accType = map.get("accountType") != null ? map.get("accountType").toString() : "SAVINGS";
                    String curr = map.get("currency") != null ? map.get("currency").toString() : "PHP";
                    String stat = map.get("status") != null ? map.get("status").toString() : "ACTIVE";

                    BigDecimal balanceVal = BigDecimal.ZERO;
                    if (map.get("availableBalance") != null) {
                        try {
                            balanceVal = new BigDecimal(map.get("availableBalance").toString());
                        } catch (Exception ignored) {}
                    }

                    String formattedBalance = formatCurrency(curr, balanceVal);

                    list.add(MobileAccountSummary.builder()
                            .accountId(accId)
                            .accountNumber(accNum)
                            .accountType(accType)
                            .currency(curr)
                            .availableBalance(balanceVal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
                            .formattedBalance(formattedBalance)
                            .status(stat)
                            .build());
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch accounts for customer [{}]: {}", customerId, ex.getMessage());
        }
        return list;
    }

    public Map<String, Object> fetchAccountByNumber(String accountNumber, String correlationId) {
        try {
            String url = org.springframework.web.util.UriComponentsBuilder.fromUriString(accountServiceBaseUrl)
                    .pathSegment("internal", "v1", "accounts", "by-number", accountNumber)
                    .build().encode().toUriString();
            log.debug("Fetching account by number from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtTokenProvider.generateServiceToken());
            if (correlationId != null && !correlationId.isBlank()) {
                headers.set("X-Correlation-Id", correlationId);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception ex) {
            log.warn("Failed to lookup account number [{}]: {}", accountNumber, ex.getMessage());
        }
        return null;
    }

    public Map<String, Object> fetchAccountById(Long accountId, String correlationId) {
        try {
            String url = org.springframework.web.util.UriComponentsBuilder.fromUriString(accountServiceBaseUrl)
                    .pathSegment("internal", "v1", "accounts", String.valueOf(accountId))
                    .build().encode().toUriString();
            log.debug("Fetching account by ID from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtTokenProvider.generateServiceToken());
            if (correlationId != null && !correlationId.isBlank()) {
                headers.set("X-Correlation-Id", correlationId);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception ex) {
            log.warn("Failed to lookup account ID [{}]: {}", accountId, ex.getMessage());
        }
        return null;
    }

    private String formatCurrency(String currency, BigDecimal amount) {
        String symbol = "PHP".equalsIgnoreCase(currency) ? "₱" : "$";
        return symbol + String.format(Locale.US, "%,.2f", amount);
    }
}
