package com.group2.fse.auth_service.service;

import com.group2.fse.auth_service.dto.*;
import com.group2.fse.auth_service.exception.InvalidCredentialsException;
import com.group2.fse.auth_service.repository.AppUserRepository;
import com.group2.fse.auth_service.security.blacklist.TokenBlacklistService;
import com.group2.fse.auth_service.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 4: Auth Business Logic Services Integration Tests")
class AuthBusinessServiceIntegrationTest {

    @Autowired
    private CustomerAuthService customerAuthService;

    @Autowired
    private StaffAuthService staffAuthService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        String encoded = passwordEncoder.encode("Password123!");
        appUserRepository.findByUsername("admin").ifPresent(u -> {
            u.setPasswordHash(encoded);
            appUserRepository.save(u);
        });
        appUserRepository.findByUsername("teller_alice").ifPresent(u -> {
            u.setPasswordHash(encoded);
            appUserRepository.save(u);
        });
        appUserRepository.findByUsername("john_doe").ifPresent(c -> {
            c.setPasswordHash(encoded);
            appUserRepository.save(c);
        });
    }

    @Test
    @DisplayName("Should authenticate seed customer with correct password and issue valid JWT pair")
    void shouldAuthenticateSeedCustomer() {
        LoginRequestDto loginDto = LoginRequestDto.builder()
                .username("john_doe")
                .password("Password123!")
                .build();

        LoginResultDto result = customerAuthService.login(loginDto);

        assertThat(result.isMfaRequired()).isFalse();
        assertThat(result.getAuthData()).isNotNull();
        assertThat(result.getAuthData().getUsername()).isEqualTo("john_doe");
        assertThat(result.getAuthData().getRoles()).containsExactly("ROLE_CUSTOMER");
        assertThat(result.getAuthData().getUserId()).isEqualTo(1L); // Handshake H1: customerId
        assertThat(jwtTokenProvider.validateToken(result.getAuthData().getAccessToken())).isTrue();
    }

    @Test
    @DisplayName("Should reject customer login with incorrect password")
    void shouldRejectCustomerWithInvalidPassword() {
        LoginRequestDto badPass = LoginRequestDto.builder()
                .username("john_doe")
                .password("WrongPassword999!")
                .build();

        assertThatThrownBy(() -> customerAuthService.login(badPass))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    @DisplayName("Should authenticate seed admin and inherit teller role")
    void shouldAuthenticateAdminWithInheritedRoles() {
        LoginRequestDto adminLogin = LoginRequestDto.builder()
                .username("admin")
                .password("Password123!")
                .build();

        LoginResultDto result = staffAuthService.login(adminLogin);

        assertThat(result.isMfaRequired()).isFalse();
        assertThat(result.getAuthData()).isNotNull();
        assertThat(result.getAuthData().getUsername()).isEqualTo("admin");
        assertThat(result.getAuthData().getRoles()).contains("ROLE_ADMIN", "ROLE_TELLER");
        assertThat(result.getAuthData().getUserType()).isEqualTo("STAFF");
    }

    @Test
    @DisplayName("Should authenticate seed teller staff")
    void shouldAuthenticateTellerStaff() {
        LoginRequestDto tellerLogin = LoginRequestDto.builder()
                .username("teller_alice")
                .password("Password123!")
                .build();

        LoginResultDto result = staffAuthService.login(tellerLogin);

        assertThat(result.isMfaRequired()).isFalse();
        assertThat(result.getAuthData()).isNotNull();
        assertThat(result.getAuthData().getUsername()).isEqualTo("teller_alice");
        assertThat(result.getAuthData().getRoles()).containsExactly("ROLE_TELLER");
    }

    @Test
    @DisplayName("Should rotate refresh token and issue fresh access token")
    void shouldRotateRefreshToken() {
        LoginResultDto loginResult = customerAuthService.login(LoginRequestDto.builder()
                .username("john_doe")
                .password("Password123!")
                .build());

        String initialRefreshToken = loginResult.getAuthData().getRefreshToken();

        AuthResponseDto refreshed = tokenService.refreshAccessToken(initialRefreshToken);

        assertThat(refreshed.getAccessToken()).isNotBlank();
        assertThat(refreshed.getRefreshToken()).isNotBlank();
        assertThat(refreshed.getRefreshToken()).isNotEqualTo(initialRefreshToken);
        assertThat(refreshed.getUsername()).isEqualTo("john_doe");
        assertThat(jwtTokenProvider.validateToken(refreshed.getAccessToken())).isTrue();
    }

    @Test
    @DisplayName("Should revoke token session and write jti to blacklist")
    void shouldRevokeTokenSession() {
        LoginResultDto loginResult = customerAuthService.login(LoginRequestDto.builder()
                .username("john_doe")
                .password("Password123!")
                .build());

        String accessToken = loginResult.getAuthData().getAccessToken();
        String jti = jwtTokenProvider.getJti(accessToken);

        assertThat(tokenBlacklistService.isRevoked(jti)).isFalse();

        tokenService.revokeToken("Bearer " + accessToken);

        assertThat(tokenBlacklistService.isRevoked(jti)).isTrue();
    }

    @Test
    @DisplayName("Should retrieve full user profile from Bearer token")
    void shouldGetUserProfileFromToken() {
        LoginResultDto loginResult = customerAuthService.login(LoginRequestDto.builder()
                .username("john_doe")
                .password("Password123!")
                .build());

        String accessToken = loginResult.getAuthData().getAccessToken();

        UserProfileDto profile = tokenService.getCurrentUserProfile("Bearer " + accessToken);

        assertThat(profile.getUsername()).isEqualTo("john_doe");
        assertThat(profile.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(profile.getUserType()).isEqualTo("CUSTOMER");
        assertThat(profile.getUserId()).isEqualTo(1L);
        assertThat(profile.getStatus()).isEqualTo("ACTIVE");
    }
}
