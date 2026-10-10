package com.group2.fse.auth_service.service;

import com.group2.fse.auth_service.dto.AuthResponseDto;
import com.group2.fse.auth_service.entity.AppUser;
import com.group2.fse.auth_service.exception.InvalidTokenException;
import com.group2.fse.auth_service.repository.AppUserRepository;
import com.group2.fse.auth_service.security.blacklist.TokenBlacklistService;
import com.group2.fse.auth_service.security.jwt.JwtTokenProvider;
import com.group2.fse.auth_service.security.session.RedisRefreshTokenService;
import com.group2.fse.auth_service.security.session.RefreshTokenData;
import com.group2.fse.auth_service.service.impl.TokenServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Needs no database and no Redis, unlike the integration tests beside it. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Token refresh reads the user again")
class TokenServiceImplTest {

    private static final String OLD = "old-refresh-token";

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RedisRefreshTokenService refreshTokens;
    @Mock
    private TokenBlacklistService blacklist;
    @Mock
    private AppUserRepository users;

    @InjectMocks
    private TokenServiceImpl tokenService;

    private void storedTokenFor(String username) {
        when(refreshTokens.getRefreshTokenData(OLD)).thenReturn(Optional.of(RefreshTokenData.builder()
                .refreshToken(OLD).userId(1L).username(username).roles(List.of("ROLE_CUSTOMER")).userType("CUSTOMER")
                .build()));
    }

    private static AppUser user(String status) {
        AppUser user = new AppUser();
        user.setUsername("john_doe");
        user.setStatus(status);
        return user;
    }

    @Test
    @DisplayName("an active user gets a new pair, and the old refresh token is spent")
    void activeUserIsRefreshed() {
        storedTokenFor("john_doe");
        when(users.findByUsername("john_doe")).thenReturn(Optional.of(user("ACTIVE")));
        when(refreshTokens.createRefreshToken(1L, "john_doe", List.of("ROLE_CUSTOMER"), "CUSTOMER")).thenReturn("new");
        when(jwtTokenProvider.generateAccessToken("john_doe", 1L, List.of("ROLE_CUSTOMER"), "CUSTOMER")).thenReturn("jwt");

        AuthResponseDto response = tokenService.refreshAccessToken(OLD);

        assertThat(response.getAccessToken()).isEqualTo("jwt");
        assertThat(response.getRefreshToken()).isEqualTo("new");
        verify(refreshTokens).revokeRefreshToken(OLD);
    }

    @Test
    @DisplayName("a user deactivated since signing in gets nothing, and the refresh token is spent")
    void deactivatedUserIsRefused() {
        storedTokenFor("john_doe");
        when(users.findByUsername("john_doe")).thenReturn(Optional.of(user("SUSPENDED")));

        assertThatThrownBy(() -> tokenService.refreshAccessToken(OLD)).isInstanceOf(InvalidTokenException.class);

        verify(refreshTokens).revokeRefreshToken(OLD);
        verify(refreshTokens, never()).createRefreshToken(any(), any(), any(), any());
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a user that no longer exists is refused the same way")
    void removedUserIsRefused() {
        storedTokenFor("gone");
        when(users.findByUsername("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.refreshAccessToken(OLD)).isInstanceOf(InvalidTokenException.class);

        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
    }
}
