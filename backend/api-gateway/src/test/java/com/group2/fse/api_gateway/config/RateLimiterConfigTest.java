package com.group2.fse.api_gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

class RateLimiterConfigTest {

    private RateLimiterConfig rateLimiterConfig;

    @BeforeEach
    void setUp() {
        rateLimiterConfig = new RateLimiterConfig();
    }

    @Test
    @DisplayName("ipKeyResolver ignores X-Forwarded-For, which the caller controls")
    void ipKeyResolver_withXForwardedFor_stillResolvesRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login")
                .header("X-Forwarded-For", "198.51.100.42, 192.168.1.1")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(rateLimiterConfig.ipKeyResolver().resolve(exchange))
                .expectNext("ip_10.0.0.5")
                .verifyComplete();
    }

    @Test
    @DisplayName("ipKeyResolver should resolve remote address when X-Forwarded-For is missing")
    void ipKeyResolver_withoutXForwardedFor_resolvesRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(rateLimiterConfig.ipKeyResolver().resolve(exchange))
                .expectNext("ip_10.0.0.5")
                .verifyComplete();
    }

    @Test
    @DisplayName("ipKeyResolver should fall back to ip_anonymous when remote address is null")
    void ipKeyResolver_withoutRemoteAddress_resolvesAnonymous() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(rateLimiterConfig.ipKeyResolver().resolve(exchange))
                .expectNext("ip_anonymous")
                .verifyComplete();
    }

    @Test
    @DisplayName("userOrIpKeyResolver should resolve user sub from Bearer JWT")
    void userOrIpKeyResolver_withJwtBearerToken_resolvesUserSub() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"david_kim\",\"role\":\"CUSTOMER\"}".getBytes(StandardCharsets.UTF_8));
        String mockJwt = header + "." + payload + ".fakesignature";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/ledger/transfers")
                .header("Authorization", "Bearer " + mockJwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(rateLimiterConfig.userOrIpKeyResolver().resolve(exchange))
                .expectNext("user_david_kim")
                .verifyComplete();
    }

    @Test
    @DisplayName("userOrIpKeyResolver should resolve user sub from Bearer JWT with formatted spacing")
    void userOrIpKeyResolver_withSpacedJwtJson_resolvesUserSub() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\": \"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\n  \"sub\": \"sarah_connor\",\n  \"role\": \"CUSTOMER\"\n}".getBytes(StandardCharsets.UTF_8));
        String mockJwt = header + "." + payload + ".fakesignature";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/ledger/transfers")
                .header("Authorization", "Bearer " + mockJwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(rateLimiterConfig.userOrIpKeyResolver().resolve(exchange))
                .expectNext("user_sarah_connor")
                .verifyComplete();
    }

    @Test
    @DisplayName("userOrIpKeyResolver should fallback to ipKeyResolver when Authorization header is absent")
    void userOrIpKeyResolver_withoutAuthHeader_fallsBackToIp() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/accounts")
                .remoteAddress(new InetSocketAddress("203.0.113.88", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(rateLimiterConfig.userOrIpKeyResolver().resolve(exchange))
                .expectNext("ip_203.0.113.88")
                .verifyComplete();
    }
}
