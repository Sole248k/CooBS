package com.group2.fse.ledger_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.ledger_service.security.blacklist.TokenBlacklistService;
import com.group2.fse.ledger_service.security.config.CorsConfig;
import com.group2.fse.ledger_service.security.config.SecurityConfig;
import com.group2.fse.ledger_service.security.filter.JwtAuthenticationFilter;
import com.group2.fse.ledger_service.security.handler.CustomAccessDeniedHandler;
import com.group2.fse.ledger_service.security.handler.CustomAuthenticationEntryPoint;
import com.group2.fse.ledger_service.security.jwt.JwtTokenProvider;
import com.group2.fse.ledger_service.security.jwt.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.FilterChainProxy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who may call what (contract sections 2.3 and 3): /api/v1/ledger/** is for Teller and Admin,
 * /internal/v1/** is for a service token, and a customer token reaches neither.
 * Requests run through the real filter chain; the token is the role name.
 */
@DisplayName("Ledger access rules")
class LedgerAccessRulesTest {

    @Configuration
    static class TestMockConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public JwtTokenProvider jwtTokenProvider() {
            JwtTokenProvider provider = mock(JwtTokenProvider.class);
            when(provider.validateToken(anyString())).thenReturn(true);
            when(provider.getAuthentication(anyString())).thenAnswer(i -> {
                String role = i.getArgument(0);
                UserPrincipal principal = UserPrincipal.create(1L, "user", List.of(role));
                return new UsernamePasswordAuthenticationToken(principal, role, principal.getAuthorities());
            });
            return provider;
        }

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
            return new JwtAuthenticationFilter(tokenProvider);
        }

        @Bean
        public TokenBlacklistService tokenBlacklistService() {
            return mock(TokenBlacklistService.class);
        }

        @Bean
        public CustomAuthenticationEntryPoint customAuthenticationEntryPoint(ObjectMapper objectMapper) {
            return new CustomAuthenticationEntryPoint(objectMapper);
        }

        @Bean
        public CustomAccessDeniedHandler customAccessDeniedHandler(ObjectMapper objectMapper) {
            return new CustomAccessDeniedHandler(objectMapper);
        }
    }

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(TestMockConfig.class, CorsConfig.class, SecurityConfig.class);

    @ParameterizedTest(name = "{0} {1} {2} -> allowed={3}")
    @CsvSource({
            "ROLE_CUSTOMER, POST, /api/v1/ledger/debits, false",
            "ROLE_CUSTOMER, POST, /api/v1/ledger/credits, false",
            "ROLE_CUSTOMER, POST, /api/v1/ledger/transfers, false",
            "ROLE_CUSTOMER, GET,  /api/v1/ledger/accounts/1/balance, false",
            "ROLE_CUSTOMER, POST, /api/v1/ledger/reversals, false",
            "ROLE_CUSTOMER, POST, /internal/v1/ledger/holds, false",
            "ROLE_TELLER,   POST, /api/v1/ledger/debits, true",
            "ROLE_ADMIN,    POST, /api/v1/ledger/transfers, true",
            "ROLE_TELLER,   GET,  /api/v1/ledger/accounts/1/balance, true",
            "ROLE_TELLER,   POST, /internal/v1/ledger/reversals, false",
            "ROLE_SERVICE,  POST, /internal/v1/ledger/reversals, true",
            "ROLE_SERVICE,  POST, /internal/v1/ledger/holds, true",
            "ROLE_SERVICE,  POST, /api/v1/ledger/debits, false"
    })
    void accessRules(String role, String method, String path, boolean allowed) {
        contextRunner.run(context -> {
            MockHttpServletRequest request = new MockHttpServletRequest(method, path);
            request.setServletPath(path);
            request.addHeader("Authorization", "Bearer " + role);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            context.getBean(FilterChainProxy.class).doFilter(request, response, chain);

            if (allowed) {
                assertThat(chain.getRequest()).as("request reached the controller").isNotNull();
            } else {
                assertThat(chain.getRequest()).as("request was stopped by the filter chain").isNull();
                assertThat(response.getStatus()).isEqualTo(403);
            }
        });
    }
}
