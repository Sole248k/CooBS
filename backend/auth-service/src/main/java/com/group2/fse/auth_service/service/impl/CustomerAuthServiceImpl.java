package com.group2.fse.auth_service.service.impl;

import com.group2.fse.auth_service.dto.AuthResponseDto;
import com.group2.fse.auth_service.dto.LoginRequestDto;
import com.group2.fse.auth_service.dto.LoginResultDto;
import com.group2.fse.auth_service.entity.AppUser;
import com.group2.fse.auth_service.exception.AccountInactiveException;
import com.group2.fse.auth_service.exception.InvalidCredentialsException;
import com.group2.fse.auth_service.repository.AppUserRepository;
import com.group2.fse.auth_service.security.jwt.JwtTokenProvider;
import com.group2.fse.auth_service.security.session.RedisRefreshTokenService;
import com.group2.fse.auth_service.security.session.SingleSessionGuard;
import com.group2.fse.auth_service.service.CustomerAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SingleSessionGuard singleSessionGuard;

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

        AuthResponseDto authData = issueTokenPair(customerId, customer.getUsername(), roles, "CUSTOMER");
        return LoginResultDto.builder()
                .mfaRequired(false)
                .authData(authData)
                .build();
    }

    private AuthResponseDto issueTokenPair(Long userId, String username, List<String> roles, String userType) {
        String accessToken = jwtTokenProvider.generateAccessToken(username, userId, roles, userType);
        String refreshToken = refreshTokenService.createRefreshToken(userId, username, roles, userType);
        singleSessionGuard.claim(userType, userId, refreshToken, accessToken, null);

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
}
