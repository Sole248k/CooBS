package com.group2.fse.customer_service.client;

import com.group2.fse.customer_service.dto.Customer360Response.AccountDto;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountClientTest {

    @Mock
    private RestTemplate restTemplate;

    private JwtTokenProvider jwtTokenProvider;
    private AccountClient accountClient;

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L);
        jwtTokenProvider.init();

        accountClient = new AccountClient(restTemplate, "http://localhost:8082", jwtTokenProvider);
    }

    @Test
    @DisplayName("fetchCustomerAccounts sends Bearer token with ROLE_SERVICE and maps accounts")
    void fetchCustomerAccounts_success() {
        List<Map<String, Object>> mockResponse = List.of(
                Map.of(
                        "accountId", 101,
                        "accountNumber", "ACC_10000001",
                        "accountType", "SAVINGS",
                        "currency", "PHP",
                        "status", "ACTIVE",
                        "availableBalance", "5000.50"
                ),
                Map.of(
                        "accountId", 102,
                        "accountNumber", "LN_10000002",
                        "accountType", "LOAN",
                        "currency", "PHP",
                        "status", "ACTIVE",
                        "availableBalance", "25000.00"
                )
        );

        when(restTemplate.exchange(
                eq("http://localhost:8082/internal/v1/customers/1/accounts"),
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

            return new ResponseEntity<>(mockResponse, HttpStatus.OK);
        });

        List<AccountDto> accounts = accountClient.fetchCustomerAccounts(1L);

        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).getAccountId()).isEqualTo(101L);
        assertThat(accounts.get(0).getAccountNumber()).isEqualTo("ACC_10000001");
        assertThat(accounts.get(0).getAccountType()).isEqualTo("SAVINGS");
        assertThat(accounts.get(0).getAvailableBalance()).isEqualTo("5000.50");

        assertThat(accounts.get(1).getAccountId()).isEqualTo(102L);
        assertThat(accounts.get(1).getAccountNumber()).isEqualTo("LN_10000002");
        assertThat(accounts.get(1).getAccountType()).isEqualTo("LOAN");
        assertThat(accounts.get(1).getAvailableBalance()).isEqualTo("25000.00");
    }

    @Test
    @DisplayName("fetchCustomerAccounts handles error gracefully and returns empty list")
    void fetchCustomerAccounts_onError_returnsEmptyList() {
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Connection refused"));

        List<AccountDto> accounts = accountClient.fetchCustomerAccounts(1L);

        assertThat(accounts).isEmpty();
    }
}
