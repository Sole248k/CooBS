package com.group2.fse.customer_service.client;

import com.group2.fse.customer_service.dto.Customer360Response.SpendingBreakdown;
import com.group2.fse.customer_service.dto.Customer360Response.SpendingItemDto;
import com.group2.fse.customer_service.dto.Customer360Response.TransactionSummaryDto;
import com.group2.fse.customer_service.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionClientTest {

    @Mock
    private RestTemplate restTemplate;

    private JwtTokenProvider jwtTokenProvider;
    private TransactionClient transactionClient;

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L);
        jwtTokenProvider.init();

        transactionClient = new TransactionClient(restTemplate, "http://localhost:8090", jwtTokenProvider);
    }

    @Test
    @DisplayName("fetchRecentTransactions sends Bearer token with ROLE_SERVICE and maps transactions")
    void fetchRecentTransactions_success() {
        List<TransactionSummaryDto> mockTransactions = List.of(
                TransactionSummaryDto.builder()
                        .referenceNo("TXN-1001")
                        .direction("OUT")
                        .amount("1500.00")
                        .category("UTILITIES")
                        .build(),
                TransactionSummaryDto.builder()
                        .referenceNo("TXN-1002")
                        .direction("OUT")
                        .amount("250.00")
                        .category("FOOD")
                        .build()
        );

        when(restTemplate.exchange(
                eq("http://localhost:8090/internal/v1/transactions?customerId=1&limit=5"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenAnswer(invocation -> {
            HttpEntity<?> entity = invocation.getArgument(2);
            HttpHeaders headers = entity.getHeaders();
            String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);

            assertThat(authHeader).isNotNull();
            assertThat(authHeader).startsWith("Bearer ");

            String token = authHeader.substring(7);
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.getRoles(token)).contains("ROLE_SERVICE");
            assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("customer-service");

            return new ResponseEntity<>(mockTransactions, HttpStatus.OK);
        });

        List<TransactionSummaryDto> result = transactionClient.fetchRecentTransactions(1L, 5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReferenceNo()).isEqualTo("TXN-1001");
        assertThat(result.get(0).getAmount()).isEqualTo("1500.00");
        assertThat(result.get(1).getReferenceNo()).isEqualTo("TXN-1002");
    }

    @Test
    @DisplayName("fetchRecentTransactions handles errors gracefully and returns empty list")
    void fetchRecentTransactions_onError_returnsEmptyList() {
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Connection timed out"));

        List<TransactionSummaryDto> result = transactionClient.fetchRecentTransactions(1L, 5);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchSpendingBreakdown sends Bearer token with ROLE_SERVICE and maps breakdown")
    void fetchSpendingBreakdown_success() {
        SpendingBreakdown mockBreakdown = SpendingBreakdown.builder()
                .periodDays(30)
                .items(List.of(
                        SpendingItemDto.builder().category("FOOD").amount("2500.00").count(5).build()
                ))
                .build();

        when(restTemplate.exchange(
                eq("http://localhost:8090/internal/v1/transactions/spending?customerId=1&days=30"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SpendingBreakdown.class)
        )).thenAnswer(invocation -> {
            HttpEntity<?> entity = invocation.getArgument(2);
            HttpHeaders headers = entity.getHeaders();
            String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);

            assertThat(authHeader).isNotNull();
            assertThat(authHeader).startsWith("Bearer ");

            String token = authHeader.substring(7);
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
            assertThat(jwtTokenProvider.getRoles(token)).contains("ROLE_SERVICE");

            return new ResponseEntity<>(mockBreakdown, HttpStatus.OK);
        });

        SpendingBreakdown result = transactionClient.fetchSpendingBreakdown(1L, 30);

        assertThat(result).isNotNull();
        assertThat(result.getPeriodDays()).isEqualTo(30);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getCategory()).isEqualTo("FOOD");
        assertThat(result.getItems().get(0).getAmount()).isEqualTo("2500.00");
    }

    @Test
    @DisplayName("fetchSpendingBreakdown handles errors gracefully and returns empty breakdown")
    void fetchSpendingBreakdown_onError_returnsEmptyBreakdown() {
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(SpendingBreakdown.class)
        )).thenThrow(new RuntimeException("Service unavailable"));

        SpendingBreakdown result = transactionClient.fetchSpendingBreakdown(1L, 30);

        assertThat(result).isNotNull();
        assertThat(result.getPeriodDays()).isEqualTo(30);
        assertThat(result.getItems()).isEmpty();
    }
}
