package com.group2.fse.auth_service.security.session;

import com.group2.fse.auth_service.security.blacklist.TokenBlacklistService;
import com.group2.fse.auth_service.security.blacklist.impl.RedisTokenBlacklistServiceImpl;
import com.group2.fse.auth_service.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Phase 3: Redis Session State & Token Blacklist Tests")
class RedisSessionStateTest {

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private RedisRefreshTokenService redisRefreshTokenService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("Should blacklist token by jti and verify key in Redis")
    void shouldBlacklistTokenByJti() {
        String testJti = "jti-test-" + System.currentTimeMillis();

        assertThat(tokenBlacklistService.isRevoked(testJti)).isFalse();

        tokenBlacklistService.revokeToken(testJti, Duration.ofSeconds(30));

        assertThat(tokenBlacklistService.isRevoked(testJti)).isTrue();
        assertThat(stringRedisTemplate.hasKey(RedisTokenBlacklistServiceImpl.KEY_PREFIX + testJti)).isTrue();
    }

    @Test
    @DisplayName("Should blacklist token directly by passing signed JWT")
    void shouldBlacklistTokenByJwt() {
        String token = jwtTokenProvider.generateAccessToken("test_user", 999L, List.of("ROLE_CUSTOMER"), "CUSTOMER");
        String jti = jwtTokenProvider.getJti(token);

        assertThat(tokenBlacklistService.isRevoked(jti)).isFalse();

        tokenBlacklistService.revokeTokenByJwt("Bearer " + token);

        assertThat(tokenBlacklistService.isRevoked(jti)).isTrue();
    }

    @Test
    @DisplayName("Should create, retrieve, and revoke refresh token in Redis")
    void shouldManageRefreshTokenLifecycle() {
        Long userId = 201L;
        String username = "refresh_user";
        List<String> roles = List.of("ROLE_CUSTOMER");

        String refreshToken = redisRefreshTokenService.createRefreshToken(userId, username, roles, "CUSTOMER");
        assertThat(refreshToken).isNotBlank();

        Optional<RefreshTokenData> dataOpt = redisRefreshTokenService.getRefreshTokenData(refreshToken);
        assertThat(dataOpt).isPresent();
        assertThat(dataOpt.get().getUserId()).isEqualTo(userId);
        assertThat(dataOpt.get().getUsername()).isEqualTo(username);
        assertThat(dataOpt.get().getRoles()).containsExactly("ROLE_CUSTOMER");

        redisRefreshTokenService.revokeRefreshToken(refreshToken);
        Optional<RefreshTokenData> revokedOpt = redisRefreshTokenService.getRefreshTokenData(refreshToken);
        assertThat(revokedOpt).isEmpty();
    }
}
