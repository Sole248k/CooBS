package com.group2.fse.auth_service.security.session;

import com.group2.fse.auth_service.security.blacklist.TokenBlacklistService;
import com.group2.fse.auth_service.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Keeps a member signed in on one device: the newest sign-in wins. Redis holds the member's
 * current token pair; a new sign-in replaces it, deletes the old refresh token and blacklists
 * the old access token, so the earlier device is refused on its next request.
 * Staff are not limited.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SingleSessionGuard {

    public static final String KEY_PREFIX = "auth:session:customer:";

    // Set APP_SECURITY_SINGLE_CUSTOMER_SESSION=false to allow several devices again.
    @Value("${app.security.single-customer-session:true}")
    private boolean enabled;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisRefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Makes this token pair the user's only session.
     *
     * @param rotatedFrom the refresh token this pair was refreshed from, or null at sign-in.
     *                    When it is the current session, the same device is carrying on and its
     *                    old access token is left to run out, since requests may still be using it.
     */
    public void claim(String userType, Long userId, String refreshToken, String accessToken, String rotatedFrom) {
        if (!enabled || !"CUSTOMER".equalsIgnoreCase(userType)) {
            return;
        }
        String key = KEY_PREFIX + userId;
        String previous = stringRedisTemplate.opsForValue()
                .getAndSet(key, refreshToken + "|" + jwtTokenProvider.getJti(accessToken));
        stringRedisTemplate.expire(key, Duration.ofMillis(jwtTokenProvider.getRefreshExpirationMs()));
        if (previous == null) {
            return;
        }
        String[] parts = previous.split("\\|", 2);
        if (parts.length < 2 || parts[0].equals(rotatedFrom)) {
            return;
        }
        refreshTokenService.revokeRefreshToken(parts[0]);
        // The old token's remaining life is not known here; its full life is the upper bound.
        tokenBlacklistService.revokeToken(parts[1], Duration.ofMillis(jwtTokenProvider.getJwtExpirationMs()));
        log.info("Customer {} signed in again: the earlier session was ended", userId);
    }
}
