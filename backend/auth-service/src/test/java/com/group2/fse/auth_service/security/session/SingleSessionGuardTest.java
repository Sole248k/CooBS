package com.group2.fse.auth_service.security.session;

import com.group2.fse.auth_service.security.blacklist.TokenBlacklistService;
import com.group2.fse.auth_service.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Needs no Redis. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("A member is signed in on one device")
class SingleSessionGuardTest {

    private static final String KEY = "auth:session:customer:1";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private RedisRefreshTokenService refreshTokens;
    @Mock
    private TokenBlacklistService blacklist;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private SingleSessionGuard guard;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(guard, "enabled", true);
        when(redis.opsForValue()).thenReturn(values);
        when(jwtTokenProvider.getJti("new-jwt")).thenReturn("new-jti");
        when(jwtTokenProvider.getJwtExpirationMs()).thenReturn(900_000L);
        when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604_800_000L);
    }

    @Test
    @DisplayName("a second sign-in ends the first: its refresh token is deleted and its access token blacklisted")
    void secondSignInEndsTheFirst() {
        when(values.getAndSet(KEY, "new-refresh|new-jti")).thenReturn("old-refresh|old-jti");

        guard.claim("CUSTOMER", 1L, "new-refresh", "new-jwt", null);

        verify(refreshTokens).revokeRefreshToken("old-refresh");
        verify(blacklist).revokeToken("old-jti", Duration.ofMillis(900_000L));
        verify(redis).expire(KEY, Duration.ofMillis(604_800_000L));
    }

    @Test
    @DisplayName("a first sign-in ends nothing")
    void firstSignInEndsNothing() {
        when(values.getAndSet(KEY, "new-refresh|new-jti")).thenReturn(null);

        guard.claim("CUSTOMER", 1L, "new-refresh", "new-jwt", null);

        verifyNoInteractions(refreshTokens, blacklist);
    }

    @Test
    @DisplayName("a refresh on the same device does not blacklist its own access token")
    void refreshOnTheSameDeviceCarriesOn() {
        when(values.getAndSet(KEY, "new-refresh|new-jti")).thenReturn("old-refresh|old-jti");

        guard.claim("CUSTOMER", 1L, "new-refresh", "new-jwt", "old-refresh");

        verifyNoInteractions(refreshTokens, blacklist);
    }

    @Test
    @DisplayName("staff are not limited, and the switch turns the rule off")
    void staffAndSwitchedOffAreLeftAlone() {
        guard.claim("STAFF", 1L, "new-refresh", "new-jwt", null);
        ReflectionTestUtils.setField(guard, "enabled", false);
        guard.claim("CUSTOMER", 1L, "new-refresh", "new-jwt", null);

        verify(values, org.mockito.Mockito.never()).getAndSet(any(), any());
        verifyNoInteractions(refreshTokens, blacklist);
    }
}
