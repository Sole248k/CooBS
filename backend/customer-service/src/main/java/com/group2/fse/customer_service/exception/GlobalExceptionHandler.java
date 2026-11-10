package com.group2.fse.customer_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerNotFound(CustomerNotFoundException ex, HttpServletRequest request) {
        log.warn("Customer not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer Not Found", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(KycNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleKycNotFound(KycNotFoundException ex, HttpServletRequest request) {
        log.warn("KYC record not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "KYC_NOT_FOUND", "KYC Not Found", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(KycRequestNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleKycRequestNotFound(KycRequestNotFoundException ex, HttpServletRequest request) {
        log.warn("KYC update request not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "KYC_REQUEST_NOT_FOUND", "KYC Request Not Found", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(BeneficiaryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBeneficiaryNotFound(BeneficiaryNotFoundException ex, HttpServletRequest request) {
        log.warn("Beneficiary not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "BENEFICIARY_NOT_FOUND", "Beneficiary Not Found", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETERS", "Invalid Parameters", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "AUTH_ACCESS_DENIED", "Access Denied", "You do not have permission to access this resource", request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> invalidParams = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of("field", err.getField(), "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid value"))
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://api.corebank.local/errors/INVALID_REQUEST_PARAMETERS");
        body.put("title", "Validation Error");
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("detail", "Request failed validation checks");
        body.put("instance", request.getRequestURI());
        body.put("errorCode", "INVALID_REQUEST_PARAMETERS");
        body.put("timestamp", Instant.now().toString());
        body.put("invalidParams", invalidParams);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing request to {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Internal Server Error", "An unexpected error occurred", request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String errorCode, String title, String detail, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://api.corebank.local/errors/" + errorCode);
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", path);
        body.put("errorCode", errorCode);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
