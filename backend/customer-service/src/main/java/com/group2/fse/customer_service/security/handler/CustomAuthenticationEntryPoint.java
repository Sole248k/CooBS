package com.group2.fse.customer_service.security.handler;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String ATTR_ERROR_CODE = "SECURITY_ERROR_CODE";
    public static final String ATTR_ERROR_DETAIL = "SECURITY_ERROR_DETAIL";

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        log.warn("Unauthorized access attempt to {}: {}", request.getRequestURI(),
                authException != null ? authException.getMessage() : "Authentication exception");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        String errorCode = (String) request.getAttribute(ATTR_ERROR_CODE);
        String detail = (String) request.getAttribute(ATTR_ERROR_DETAIL);

        if (errorCode == null || errorCode.isBlank()) {
            errorCode = "AUTH_INVALID_CREDENTIALS";
        }

        if (detail == null || detail.isBlank()) {
            if ("AUTH_TOKEN_EXPIRED".equals(errorCode)) {
                detail = "Your session has expired. Please log in again to continue.";
            } else {
                detail = authException != null && authException.getMessage() != null && !authException.getMessage().isBlank()
                        ? authException.getMessage()
                        : "Full authentication is required to access this resource.";
            }
        }

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://api.corebank.local/errors/" + errorCode);
        problem.put("title", "Unauthorized");
        problem.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put("errorCode", errorCode);
        problem.put("timestamp", Instant.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
