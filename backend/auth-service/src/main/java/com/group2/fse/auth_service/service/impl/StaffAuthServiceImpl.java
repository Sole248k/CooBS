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
import com.group2.fse.auth_service.service.StaffAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffAuthServiceImpl implements StaffAuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public LoginResultDto login(LoginRequestDto request) {
        log.info("Bank staff login attempt for username: {}", request.getUsername());

        AppUser staff = appUserRepository.findByUsernameAndUserType(request.getUsername(), "STAFF")
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), staff.getPasswordHash())) {
            log.warn("Invalid password for staff username: {}", request.getUsername());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(staff.getStatus())) {
            log.warn("Staff account is not active: status={}", staff.getStatus());
            throw new AccountInactiveException("Staff account is suspended or inactive");
        }

        List<String> roles = resolveStaffRoles(staff.getRole().getRoleName());

        AuthResponseDto authData = issueTokenPair(staff.getUserId(), staff.getUsername(), roles, "STAFF");
        return LoginResultDto.builder()
                .mfaRequired(false)
                .authData(authData)
                .build();
    }

    private List<String> resolveStaffRoles(String primaryRole) {
        List<String> roles = new ArrayList<>();
        roles.add(primaryRole);
        if ("ROLE_ADMIN".equals(primaryRole) && !roles.contains("ROLE_TELLER")) {
            roles.add("ROLE_TELLER");
        }
        return roles;
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
}
