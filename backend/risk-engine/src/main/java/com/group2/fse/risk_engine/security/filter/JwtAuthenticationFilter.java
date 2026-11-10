package com.group2.fse.risk_engine.security.filter;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.group2.fse.risk_engine.security.handler.CustomAuthenticationEntryPoint;
import com.group2.fse.risk_engine.security.jwt.JwtTokenProvider;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";
    private static final String HEADER_API_KEY = "X-API-Key";

    private final JwtTokenProvider jwtTokenProvider;

    @org.springframework.beans.factory.annotation.Value("${app.security.internal-secret:coobs-risk-internal-secret-2026}")
    private String internalSecret;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            // 1. Check for Pre-Shared Internal Secret / API Key Header (FIN-405)
            String secretHeader = resolveSecret(request);
            if (StringUtils.hasText(secretHeader)) {
                if (secretHeader.equals(internalSecret)) {
                    com.group2.fse.risk_engine.security.UserPrincipal principal =
                            com.group2.fse.risk_engine.security.UserPrincipal.create(
                                    0L, "INTERNAL_SERVICE", java.util.List.of("ROLE_SERVICE"));
                    org.springframework.security.core.Authentication authentication =
                            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                    principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Authenticated internal service via shared secret for URI: {}", request.getRequestURI());
                } else {
                    log.warn("Unauthorized call to {} with invalid API secret", request.getRequestURI());
                    request.setAttribute(CustomAuthenticationEntryPoint.ATTR_ERROR_CODE, "AUTH_INVALID_CREDENTIALS");
                    request.setAttribute(CustomAuthenticationEntryPoint.ATTR_ERROR_DETAIL, "Invalid internal API secret.");
                }
            } else {
                // 2. Fallback to JWT Bearer Token Authentication
                String jwt = resolveToken(request);
                if (StringUtils.hasText(jwt)) {
                    if (jwtTokenProvider.validateToken(jwt)) {
                        Authentication authentication = jwtTokenProvider.getAuthentication(jwt);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Authenticated user '{}' with roles {} for URI: {}",
                                authentication.getName(), authentication.getAuthorities(), request.getRequestURI());
                    } else {
                        request.setAttribute(CustomAuthenticationEntryPoint.ATTR_ERROR_CODE, "AUTH_INVALID_CREDENTIALS");
                        request.setAttribute(CustomAuthenticationEntryPoint.ATTR_ERROR_DETAIL, "Invalid or expired authentication credentials.");
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String resolveSecret(HttpServletRequest request) {
        String secret = request.getHeader(HEADER_INTERNAL_SECRET);
        if (StringUtils.hasText(secret)) {
            return secret.trim();
        }
        secret = request.getHeader(HEADER_API_KEY);
        if (StringUtils.hasText(secret)) {
            return secret.trim();
        }
        return null;
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
