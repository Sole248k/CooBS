package com.group2.fse.transaction_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized exception handler producing RFC 7807 Problem Details.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateIdempotencyKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateIdempotencyKey(
            DuplicateIdempotencyKeyException ex, HttpServletRequest request) {
        log.warn("Idempotency conflict on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.coobs.local/errors/CONCURRENT_TRANSACTION_IN_FLIGHT",
                "Transaction Already In Progress",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "CONCURRENT_TRANSACTION_IN_FLIGHT"
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransaction(
            InvalidTransactionException ex, HttpServletRequest request) {
        log.warn("Invalid transaction on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.coobs.local/errors/INVALID_TRANSACTION",
                "Invalid Transaction",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "INVALID_TRANSACTION"
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed on {}: {} field error(s)",
                request.getRequestURI(),
                ex.getBindingResult() != null ? ex.getBindingResult().getErrorCount() : 0);

        List<Map<String, String>> invalidParams = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> param = new LinkedHashMap<>();
            param.put("field", fieldError.getField());
            param.put("message", fieldError.getDefaultMessage());
            invalidParams.add(param);
        }

        Map<String, Object> problem = createProblemDetails(
                "https://api.coobs.local/errors/INVALID_REQUEST_PARAMETERS",
                "Validation Failed",
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed for one or more request fields.",
                request.getRequestURI(),
                "INVALID_REQUEST_PARAMETERS"
        );
        problem.put("invalidParams", invalidParams);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        Map<String, Object> problem = createProblemDetails(
                "https://api.coobs.local/errors/INTERNAL_SERVER_ERROR",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected internal error occurred. Please try again later.",
                request.getRequestURI(),
                "INTERNAL_SERVER_ERROR"
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private Map<String, Object> createProblemDetails(
            String type, String title, int status, String detail, String instance, String errorCode) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", type);
        problem.put("title", title);
        problem.put("status", status);
        problem.put("detail", detail);
        problem.put("instance", instance);
        problem.put("errorCode", errorCode);
        problem.put("timestamp", Instant.now().toString());
        return problem;
    }
}
