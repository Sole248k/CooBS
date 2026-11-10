package com.group2.fse.customer_service.client;

import com.group2.fse.customer_service.dto.Customer360Response.SpendingBreakdown;
import com.group2.fse.customer_service.dto.Customer360Response.TransactionSummaryDto;
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

/**
 * Client for communicating with transaction-service internal endpoints (Handshake H10).
 * Queries recent transaction history (T8) and spending breakdown (T9) for Customer 360 aggregation.
 * Automatically injects internal service bearer token with ROLE_SERVICE.
 */
@Slf4j
@Component
public class TransactionClient {

    private final RestTemplate restTemplate;
    private final String transactionServiceBaseUrl;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public TransactionClient(
            @Value("${app.clients.transaction.base-url:http://localhost:8090}") String transactionServiceBaseUrl,
            @Value("${app.clients.transaction.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${app.clients.transaction.read-timeout-ms:5000}") int readTimeout,
            JwtTokenProvider jwtTokenProvider) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        this.restTemplate = new RestTemplate(factory);
        this.transactionServiceBaseUrl = transactionServiceBaseUrl;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Package-private constructor for unit and integration testing.
     */
    TransactionClient(RestTemplate restTemplate, String transactionServiceBaseUrl, JwtTokenProvider jwtTokenProvider) {
        this.restTemplate = restTemplate;
        this.transactionServiceBaseUrl = transactionServiceBaseUrl;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Fetches recent transactions for a customer from transaction-service (T8).
     *
     * @param customerId ID of customer
     * @param limit maximum transactions to return
     * @return List of TransactionSummaryDto, or empty list if call fails
     */
    public List<TransactionSummaryDto> fetchRecentTransactions(Long customerId, int limit) {
        List<TransactionSummaryDto> list = new ArrayList<>();
        try {
            int actualLimit = limit > 0 ? limit : 5;
            String url = transactionServiceBaseUrl + "/internal/v1/transactions?customerId=" + customerId + "&limit=" + actualLimit;
            log.info("Fetching recent transactions for customer 360 from transaction-service: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(generateServiceToken());

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<List<TransactionSummaryDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<TransactionSummaryDto>>() {}
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                list = response.getBody();
            }
        } catch (Exception ex) {
            log.warn("Could not retrieve recent transactions for customer {} from transaction-service: {}", customerId, ex.getMessage());
        }
        return list;
    }

    /**
     * Fetches spending category breakdown for a customer from transaction-service (T9).
     *
     * @param customerId ID of customer
     * @param days lookback period in days
     * @return SpendingBreakdown object, or empty SpendingBreakdown if call fails
     */
    public SpendingBreakdown fetchSpendingBreakdown(Long customerId, int days) {
        int actualDays = days > 0 ? days : 30;
        try {
            String url = transactionServiceBaseUrl + "/internal/v1/transactions/spending?customerId=" + customerId + "&days=" + actualDays;
            log.info("Fetching spending breakdown for customer 360 from transaction-service: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(generateServiceToken());

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<SpendingBreakdown> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    SpendingBreakdown.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception ex) {
            log.warn("Could not retrieve spending breakdown for customer {} from transaction-service: {}", customerId, ex.getMessage());
        }
        return SpendingBreakdown.builder().periodDays(actualDays).items(new ArrayList<>()).build();
    }

    private String generateServiceToken() {
        return jwtTokenProvider.generateToken(0L, "customer-service", List.of("ROLE_SERVICE"));
    }
}
