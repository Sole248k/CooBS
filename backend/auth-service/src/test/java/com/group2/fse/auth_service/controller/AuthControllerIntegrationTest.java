package com.group2.fse.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.auth_service.dto.*;
import com.group2.fse.auth_service.repository.AppUserRepository;
import com.group2.fse.auth_service.security.session.RedisRefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 5: Auth REST Controllers & RFC-7807 Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired
    private CustomerAuthController customerAuthController;

    @Autowired
    private StaffAuthController staffAuthController;

    @Autowired
    private TokenController tokenController;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RedisRefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(customerAuthController, staffAuthController, tokenController)
                .setControllerAdvice(new com.group2.fse.auth_service.exception.GlobalExceptionHandler())
                .build();

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
    @DisplayName("POST /api/v1/auth/customers/login - Valid credentials returns HTTP 200 OK")
    void shouldLoginCustomerSuccessfully() throws Exception {
        LoginRequestDto loginDto = LoginRequestDto.builder()
                .username("john_doe")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired", is(false)))
                .andExpect(jsonPath("$.authData.accessToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.authData.username", is("john_doe")))
                .andExpect(jsonPath("$.authData.roles[0]", is("ROLE_CUSTOMER")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/customers/login - Invalid password returns HTTP 401 RFC-7807")
    void shouldReturn401ForBadCustomerPassword() throws Exception {
        LoginRequestDto badLogin = LoginRequestDto.builder()
                .username("john_doe")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/v1/auth/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.errorCode", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("POST /api/v1/auth/staff/login - Admin login returns HTTP 200 OK with inherited roles")
    void shouldLoginAdminStaffSuccessfully() throws Exception {
        LoginRequestDto adminLogin = LoginRequestDto.builder()
                .username("admin")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired", is(false)))
                .andExpect(jsonPath("$.authData.username", is("admin")))
                .andExpect(jsonPath("$.authData.roles", hasItems("ROLE_ADMIN", "ROLE_TELLER")))
                .andExpect(jsonPath("$.authData.userType", is("STAFF")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/staff/login - Unknown username returns HTTP 401 RFC-7807")
    void shouldReturn401ForUnknownStaff() throws Exception {
        LoginRequestDto unknownStaff = LoginRequestDto.builder()
                .username("non_existent_staff")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownStaff)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.errorCode", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("POST /api/v1/auth/token/refresh - Valid refresh token rotates and returns HTTP 200 OK")
    void shouldRotateRefreshTokenViaRest() throws Exception {
        String refreshToken = refreshTokenService.createRefreshToken(
                1L, "john_doe", List.of("ROLE_CUSTOMER"), "CUSTOMER");

        RefreshTokenRequestDto refreshDto = RefreshTokenRequestDto.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(refreshToken)))
                .andExpect(jsonPath("$.username", is("john_doe")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/token/refresh - Expired or bad token returns HTTP 401 RFC-7807")
    void shouldReturn401ForBadRefreshToken() throws Exception {
        RefreshTokenRequestDto badDto = RefreshTokenRequestDto.builder()
                .refreshToken("bad-or-expired-refresh-token")
                .build();

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badDto)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.errorCode", is("INVALID_TOKEN")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/token/revoke - Valid token returns HTTP 204 No Content")
    void shouldRevokeTokenViaRest() throws Exception {
        LoginRequestDto loginDto = LoginRequestDto.builder()
                .username("john_doe")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseJson).path("authData").path("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/token/revoke")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Subsequent call to /me using the revoked token should return 401
        mockMvc.perform(get("/api/v1/auth/token/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.errorCode", is("INVALID_TOKEN")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/token/me - Valid token returns HTTP 200 OK with UserProfileDto")
    void shouldReturnUserProfileViaRest() throws Exception {
        LoginRequestDto loginDto = LoginRequestDto.builder()
                .username("john_doe")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseJson).path("authData").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/token/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("john_doe")))
                .andExpect(jsonPath("$.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.userType", is("CUSTOMER")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/token/me - Missing Authorization header returns HTTP 401 RFC-7807")
    void shouldReturn401WhenAuthHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/token/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.errorCode", is("INVALID_TOKEN")));
    }
}
