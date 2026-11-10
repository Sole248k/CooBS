package com.group2.fse.auth_service.security.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisRefreshTokenService {

    public static final String KEY_PREFIX = "auth:refresh:";

    @Value("${app.security.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Creates and saves a new refresh token in Redis with a 7-day TTL.
     */
    public String createRefreshToken(Long userId, String username, List<String> roles, String userType) {
        String token = UUID.randomUUID().toString();
        RefreshTokenData data = RefreshTokenData.builder()
                .refreshToken(token)
                .userId(userId)
                .username(username)
                .roles(roles)
                .userType(userType)
                .createdAt(Instant.now())
                .build();

        try {
            String json = objectMapper.writeValueAsString(data);
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + token, json, Duration.ofMillis(refreshExpirationMs));
            log.info("Created refresh token in Redis for user {}", username);
            return token;
        } catch (Exception e) {
            log.error("Failed to serialize refresh token data: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to store refresh token in Redis", e);
        }
    }

    /**
     * Looks up and parses a refresh token from Redis.
     */
    public Optional<RefreshTokenData> getRefreshTokenData(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + refreshToken);
        if (json == null) {
            return Optional.empty();
        }
        try {
            RefreshTokenData data = objectMapper.readValue(json, RefreshTokenData.class);
            return Optional.of(data);
        } catch (Exception e) {
            log.error("Failed to parse refresh token from Redis: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Revokes a refresh token by deleting it from Redis.
     */
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            stringRedisTemplate.delete(KEY_PREFIX + refreshToken);
            log.info("Revoked a refresh token");
        }
    }
}
