package com.group2.fse.staff_bff.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("API Exception on [{}]: {} ({})", request.getRequestURI(), ex.getMessage(), ex.getErrorCode());
        return buildErrorResponse(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> invalidParams = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            invalidParams.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation failure on [{}]: {}", request.getRequestURI(), invalidParams);

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_PARAMETERS",
                "Validation failed for one or more fields",
                request.getRequestURI(),
                invalidParams
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "AUTH_FORBIDDEN",
                "You do not have permission to perform this action. Staff access required.",
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failure on [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "AUTH_UNAUTHORIZED",
                "Authentication required. Valid staff token must be provided.",
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(org.springframework.web.client.HttpStatusCodeException.class)
    public ResponseEntity<Map<String, Object>> handleHttpStatusCodeException(
            org.springframework.web.client.HttpStatusCodeException ex, HttpServletRequest request) {
        log.warn("Downstream service error on [{}]: status={}, body={}", request.getRequestURI(), ex.getStatusCode(), ex.getResponseBodyAsString());
        String body = ex.getResponseBodyAsString();
        String detail = ex.getMessage();
        String errorCode = "DOWNSTREAM_SERVICE_ERROR";
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<?, ?> map = mapper.readValue(body, Map.class);
            if (map.containsKey("detail") && map.get("detail") != null) {
                detail = map.get("detail").toString();
            } else if (map.containsKey("message") && map.get("message") != null) {
                detail = map.get("message").toString();
            }
            if (map.containsKey("errorCode") && map.get("errorCode") != null) {
                errorCode = map.get("errorCode").toString();
            }
        } catch (Exception ignored) {
        }
        return buildErrorResponse(
                HttpStatus.valueOf(ex.getStatusCode().value()),
                errorCode,
                detail,
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal server error on [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred. Please contact system operations.",
                request.getRequestURI(),
                null
        );
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status,
            String errorCode,
            String detail,
            String path,
            Map<String, String> invalidParams) {

        Map<String, Object> body = new HashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("errorCode", errorCode != null ? errorCode : status.name());
        body.put("detail", detail);
        body.put("path", path);
        body.put("timestamp", Instant.now().toString());

        if (invalidParams != null && !invalidParams.isEmpty()) {
            body.put("invalidParams", invalidParams);
        }

        return ResponseEntity.status(status).body(body);
    }
}
