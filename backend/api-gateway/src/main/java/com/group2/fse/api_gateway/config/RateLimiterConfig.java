package com.group2.fse.api_gateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Enterprise Rate Limiting Key Resolvers for CooBS Core Banking API Gateway.
 * Backed by Redis Token Bucket algorithm.
 */
@Slf4j
@Configuration
public class RateLimiterConfig {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * IP-Based Key Resolver:
     * Resolves the originating client IP address, properly handling X-Forwarded-For proxies.
     * Used for public / unauthenticated endpoints such as /api/v1/auth/login and /register
     * to protect against credential brute force and denial of service.
     */
    @Primary
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
            if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
                String clientIp = forwardedFor.split(",")[0].trim();
                if (!clientIp.isEmpty() && clientIp.matches("^[a-fA-F0-9.:]+$")) {
                    return Mono.just("ip_" + clientIp);
                }
            }

            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just("ip_" + remoteAddress.getAddress().getHostAddress());
            }

            return Mono.just("ip_anonymous");
        };
    }

    /**
     * User or IP Key Resolver:
     * Resolves the authenticated user from the Authorization Bearer token.
     * Falls back to client IP address if no Bearer token is present.
     * Used for authenticated business operations (Ledger, Accounts, Audit, Notifications).
     */
    @Bean
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
            if (authHeader != null && authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                String token = authHeader.substring(BEARER_PREFIX.length()).trim();
                if (!token.isEmpty()) {
                    // Extract subject or use a prefix hash of the token to key per authenticated user
                    return Mono.just("user_" + extractUserOrTokenKey(token));
                }
            }

            // Fallback to client IP resolution if unauthenticated
            return ipKeyResolver().resolve(exchange);
        };
    }

    /**
     * Helper to derive a compact, deterministic user key from JWT or token.
     */
    private String extractUserOrTokenKey(String token) {
        String[] parts = token.split("\\.");
        if (parts.length >= 2) {
            try {
                byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
                JsonNode payload = OBJECT_MAPPER.readTree(decoded);
                if (payload != null && payload.hasNonNull("sub")) {
                    String sub = payload.get("sub").asText().trim();
                    if (!sub.isEmpty()) {
                        return sub;
                    }
                }
            } catch (Exception ignored) {
                // Fallback to token hash if payload parsing fails
            }
        }
        return Integer.toHexString(token.hashCode());
    }
}
