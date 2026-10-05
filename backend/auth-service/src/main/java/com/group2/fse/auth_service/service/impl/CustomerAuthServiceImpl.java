package com.group2.fse.auth_service.service.impl;

import com.group2.fse.auth_service.dto.*;
import com.group2.fse.auth_service.entity.AppUser;
import com.group2.fse.auth_service.exception.AccountInactiveException;
import com.group2.fse.auth_service.exception.InvalidCredentialsException;
import com.group2.fse.auth_service.exception.MfaVerificationException;
import com.group2.fse.auth_service.repository.AppUserRepository;
import com.group2.fse.auth_service.security.jwt.JwtTokenProvider;
import com.group2.fse.auth_service.security.mfa.MfaChallenge;
import com.group2.fse.auth_service.security.mfa.MfaChallengeService;
import com.group2.fse.auth_service.security.session.RedisRefreshTokenService;
import com.group2.fse.auth_service.service.CustomerAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerAuthServiceImpl implements CustomerAuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRefreshTokenService refreshTokenService;
    private final MfaChallengeService mfaChallengeService;

    @Value("${app.security.mfa.enabled:false}")
    private boolean mfaEnabled;



    @Override
    @Transactional(readOnly = true)
    public LoginResultDto login(LoginRequestDto request) {
        log.info("Customer login attempt for username: {}", request.getUsername());

        AppUser customer = appUserRepository.findByUsernameAndUserType(request.getUsername(), "CUSTOMER")
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), customer.getPasswordHash())) {
            log.warn("Invalid password for customer username: {}", request.getUsername());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            log.warn("Customer account is not active: status={}", customer.getStatus());
            throw new AccountInactiveException("Customer account is suspended or inactive");
        }

        List<String> roles = List.of(customer.getRole() != null ? customer.getRole().getRoleName() : "ROLE_CUSTOMER");

        // Handshake H1: customerId is passed as the token's userId claim
        Long customerId = customer.getCustomerId() != null ? customer.getCustomerId() : customer.getUserId();

        if (mfaEnabled) {
            log.info("MFA challenge triggered for customer: {}", customer.getUsername());
            String channel = resolveChannel(request.getPreferredChannel(), "EMAIL");
            String destination;
            String maskedDestination;

            if ("EMAIL".equalsIgnoreCase(channel)) {
                destination = customer.getEmail();
                maskedDestination = maskEmail(customer.getEmail());
            } else if ("TOTP".equalsIgnoreCase(channel)) {
                destination = "Authenticator App (Google/Microsoft Authenticator)";
                maskedDestination = "Authenticator App (RFC 6238)";
            } else {
                channel = "EMAIL";
                destination = customer.getEmail();
                maskedDestination = maskEmail(customer.getEmail());
            }

            MfaChallenge challenge = mfaChallengeService.createChallenge(
                    customerId, customer.getUsername(), roles, "CUSTOMER", channel, destination);

            return LoginResultDto.builder()
                    .mfaRequired(true)
                    .mfaToken(challenge.getMfaToken())
                    .deliveryChannel(channel)
                    .maskedDestination(maskedDestination)
                    .build();
        }

        AuthResponseDto authData = issueTokenPair(customerId, customer.getUsername(), roles, "CUSTOMER");
        return LoginResultDto.builder()
                .mfaRequired(false)
                .authData(authData)
                .build();
    }

    @Override
    public AuthResponseDto verifyMfa(MfaVerifyRequestDto request) {
        MfaChallenge challenge = mfaChallengeService.verifyChallenge(request.getMfaToken(), request.getCode())
                .orElseThrow(() -> new MfaVerificationException("Invalid or expired 2FA verification code"));

        if (!"CUSTOMER".equals(challenge.getUserType())) {
            throw new MfaVerificationException("Invalid user type for customer MFA verification");
        }

        log.info("MFA verification successful for customer: {}", challenge.getUsername());
        return issueTokenPair(challenge.getUserId(), challenge.getUsername(), challenge.getRoles(), "CUSTOMER");
    }

    private AuthResponseDto issueTokenPair(Long userId, String username, List<String> roles, String userType) {
        String accessToken = jwtTokenProvider.generateAccessToken(username, userId, roles, userType);
        String refreshToken = refreshTokenService.createRefreshToken(userId, username, roles, userType);

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getJwtExpirationMs() / 1000)
                .userId(userId)
                .username(username)
                .roles(roles)
                .userType(userType)
                .build();
    }

    private String resolveChannel(String preferred, String defaultChannel) {
        if (preferred == null || preferred.isBlank()) {
            return defaultChannel;
        }
        String p = preferred.trim().toUpperCase();
        if ("TOTP".equals(p) || "EMAIL".equals(p) || "SMS".equals(p)) {
            return p;
        }
        return defaultChannel;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***@***.com";
        }
        int atIdx = email.indexOf("@");
        String name = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
