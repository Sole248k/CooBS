package com.group2.fse.auth_service.service.impl;

import com.group2.fse.auth_service.dto.AuthResponseDto;
import com.group2.fse.auth_service.dto.UserProfileDto;
import com.group2.fse.auth_service.entity.AppUser;
import com.group2.fse.auth_service.exception.InvalidTokenException;
import com.group2.fse.auth_service.exception.UserNotFoundException;
import com.group2.fse.auth_service.repository.AppUserRepository;
import com.group2.fse.auth_service.security.blacklist.TokenBlacklistService;
import com.group2.fse.auth_service.security.jwt.JwtTokenProvider;
import com.group2.fse.auth_service.security.session.RedisRefreshTokenService;
import com.group2.fse.auth_service.security.session.RefreshTokenData;
import com.group2.fse.auth_service.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenServiceImpl implements TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AppUserRepository appUserRepository;

    @Override
    public AuthResponseDto refreshAccessToken(String refreshToken) {
        log.info("Attempting access token refresh");

        RefreshTokenData data = refreshTokenService.getRefreshTokenData(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired refresh token"));

        // Rotate: revoke the old refresh token, issue a new one
        refreshTokenService.revokeRefreshToken(refreshToken);
        String newRefreshToken = refreshTokenService.createRefreshToken(
                data.getUserId(), data.getUsername(), data.getRoles(), data.getUserType());

        // Issue new access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                data.getUsername(), data.getUserId(), data.getRoles(), data.getUserType());

        log.info("Successfully refreshed access token for user: {}", data.getUsername());

        return AuthResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getJwtExpirationMs() / 1000)
                .userId(data.getUserId())
                .username(data.getUsername())
                .roles(data.getRoles())
                .userType(data.getUserType())
                .build();
    }

    @Override
    public void revokeToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalArgumentException("Authorization token must not be blank");
        }
        tokenBlacklistService.revokeTokenByJwt(bearerToken);
        log.info("Token session revoked successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getCurrentUserProfile(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new IllegalArgumentException("Authorization token must not be blank");
        }
        String jwt = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;
        if (!jwtTokenProvider.validateToken(jwt)) {
            throw new InvalidTokenException("Invalid or expired authorization token");
        }

        String jti = jwtTokenProvider.getJti(jwt);
        if (jti != null && tokenBlacklistService.isRevoked(jti)) {
            log.warn("Attempt to use revoked JWT with jti={}", jti);
            throw new InvalidTokenException("Token session has been revoked");
        }

        String username = jwtTokenProvider.getUsername(jwt);
        List<String> roles = jwtTokenProvider.getRoles(jwt);

        AppUser appUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        Long resolvedUserId = "CUSTOMER".equalsIgnoreCase(appUser.getUserType())
                ? appUser.getCustomerId()
                : appUser.getUserId();

        return UserProfileDto.builder()
                .userId(resolvedUserId)
                .username(appUser.getUsername())
                .email(appUser.getEmail())
                .roles(roles)
                .userType(appUser.getUserType())
                .status(appUser.getStatus())
                .createdAt(appUser.getCreatedAt())
                .build();
    }
}
