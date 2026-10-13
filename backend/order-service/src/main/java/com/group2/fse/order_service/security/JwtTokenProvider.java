package com.group2.fse.order_service.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${app.security.jwt.secret:default-secret-key-that-is-at-least-32-bytes-long}")
    private String jwtSecret;

    @Value("${app.security.jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token.trim());
            return true;
        } catch (Exception ex) {
            log.debug("JWT verification failed: {}", ex.getMessage());
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token.trim()).getPayload();

        Long userId = null;
        Object userIdClaim = claims.get("userId");
        if (userIdClaim != null) {
            userId = Long.valueOf(userIdClaim.toString());
        } else if (claims.getSubject() != null && claims.getSubject().matches("\\d+")) {
            userId = Long.valueOf(claims.getSubject());
        }

        String username = claims.get("username", String.class);
        if (username == null) {
            username = claims.getSubject();
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?> list) {
            authorities = list.stream()
                    .map(r -> {
                        String s = r.toString();
                        return s.startsWith("ROLE_") ? new SimpleGrantedAuthority(s) : new SimpleGrantedAuthority("ROLE_" + s);
                    })
                    .collect(Collectors.toList());
        } else if (claims.get("role") != null) {
            String role = claims.get("role").toString();
            authorities.add(role.startsWith("ROLE_") ? new SimpleGrantedAuthority(role) : new SimpleGrantedAuthority("ROLE_" + role));
        }

        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .username(username)
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public String generateServiceToken() {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);
        return Jwts.builder()
                .subject("order-service")
                .claim("userId", 0L)
                .claim("roles", List.of("ROLE_SERVICE", "ROLE_ADMIN"))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }
}
