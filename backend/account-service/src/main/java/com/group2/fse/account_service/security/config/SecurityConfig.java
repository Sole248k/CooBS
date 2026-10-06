package com.group2.fse.account_service.security.config;

import com.group2.fse.account_service.security.blacklist.TokenBlacklistFilter;
import com.group2.fse.account_service.security.blacklist.TokenBlacklistService;
import com.group2.fse.account_service.security.filter.JwtAuthenticationFilter;
import com.group2.fse.account_service.security.handler.CustomAccessDeniedHandler;
import com.group2.fse.account_service.security.handler.CustomAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless Spring Security Configuration for Account Service.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TokenBlacklistService tokenBlacklistService;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/error"
                        ).permitAll()
                        
                        // Account endpoints
                        .requestMatchers("/api/v1/accounts/my-accounts").hasAnyRole("CUSTOMER", "TELLER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounts").hasAnyRole("TELLER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasAnyRole("TELLER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounts/closure-requests").hasAnyRole("TELLER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/closure-requests/*/approve").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/closure-requests/*/reject").hasRole("ADMIN")
                        .requestMatchers("/api/v1/accounts/*/closure-request").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/accounts/*/status").hasAnyRole("TELLER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/flags").hasAnyRole("TELLER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/accounts/*/flags/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*").hasAnyRole("CUSTOMER", "TELLER", "ADMIN")
                        .requestMatchers("/internal/v1/**").hasRole("SERVICE")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new TokenBlacklistFilter(tokenBlacklistService, customAuthenticationEntryPoint), JwtAuthenticationFilter.class);

        return http.build();
    }
}
