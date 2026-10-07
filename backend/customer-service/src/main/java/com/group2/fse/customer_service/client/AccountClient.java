package com.group2.fse.customer_service.client;

import com.group2.fse.customer_service.dto.Customer360Response.AccountDto;
import com.group2.fse.customer_service.security.jwt.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hybrid client for communicating with on-premises account-service.
 * Queries sovereign deposit and loan account records and live balances for Customer 360 aggregation.
 * Automatically injects internal service bearer token with ROLE_SERVICE.
 */
@Slf4j
@Component
public class AccountClient {

    private final RestTemplate restTemplate;
    private final String accountServiceBaseUrl;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public AccountClient(
            @Value("${app.clients.account.base-url:http://localhost:8082}") String accountServiceBaseUrl,
            @Value("${app.clients.account.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${app.clients.account.read-timeout-ms:3000}") int readTimeout,
            JwtTokenProvider jwtTokenProvider) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        this.restTemplate = new RestTemplate(factory);
        this.accountServiceBaseUrl = accountServiceBaseUrl;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Package-private constructor for unit and integration testing.
     */
    AccountClient(RestTemplate restTemplate, String accountServiceBaseUrl, JwtTokenProvider jwtTokenProvider) {
        this.restTemplate = restTemplate;
        this.accountServiceBaseUrl = accountServiceBaseUrl;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Fetches sovereign accounts and balances for a customer from account-service on-premises.
     *
     * @param customerId ID of customer
     * @return List of mapped AccountDto objects, or empty list if call fails
     */
    public List<AccountDto> fetchCustomerAccounts(Long customerId) {
        List<AccountDto> list = new ArrayList<>();
        try {
            String url = accountServiceBaseUrl + "/internal/v1/customers/" + customerId + "/accounts";
            log.info("Fetching accounts for customer 360 from on-prem account-service: {}", url);

            String serviceToken = generateServiceToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(serviceToken);

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

                    list.add(AccountDto.builder()
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

    private String generateServiceToken() {
        return jwtTokenProvider.generateToken(0L, "customer-service", List.of("ROLE_SERVICE"));
    }
}
