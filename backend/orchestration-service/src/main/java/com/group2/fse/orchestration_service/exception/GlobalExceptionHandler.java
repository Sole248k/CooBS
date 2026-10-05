package com.group2.fse.orchestration_service.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.group2.fse.orchestration_service.client.DownstreamException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps exceptions to the problem shape of contract section 2.5. There is deliberately no
 * catch-all handler: access-denied exceptions must reach CustomAccessDeniedHandler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> problem = problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETERS",
                "Invalid Request Parameters", "One or more fields are invalid.", request);
        List<Map<String, String>> invalidParams = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        problem.put("invalidParams", invalidParams);
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, problem(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND",
                "Transaction Not Found", ex.getMessage(), request));
    }

    /** Only the synchronous inbound inter-bank saga lets this out, when the ledger gives no answer. */
    @ExceptionHandler(DownstreamException.class)
    public ResponseEntity<Map<String, Object>> handleDownstream(DownstreamException ex, HttpServletRequest request) {
        log.error("Ledger unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, problem(HttpStatus.SERVICE_UNAVAILABLE, "LEDGER_UNAVAILABLE",
                "Ledger Unavailable", "The ledger did not answer. The request can be repeated.", request));
    }

    private static Map<String, Object> problem(HttpStatus status, String errorCode, String title, String detail,
            HttpServletRequest request) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://api.corebank.local/errors/" + errorCode);
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put("errorCode", errorCode);
        problem.put("timestamp", Instant.now().toString());
        return problem;
    }

    private static ResponseEntity<Map<String, Object>> respond(HttpStatus status, Map<String, Object> problem) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
