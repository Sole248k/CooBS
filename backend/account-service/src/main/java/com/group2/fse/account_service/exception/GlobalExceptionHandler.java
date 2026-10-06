package com.group2.fse.account_service.exception;

import com.group2.fse.account_service.security.blacklist.TokenRevokedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
 * Global REST exception advisor mapping domain exceptions to RFC-7807 application/problem+json envelopes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotFound(
            AccountNotFoundException ex, HttpServletRequest request) {
        log.warn("Account not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse("https://api.corebank.local/errors/ACCOUNT_NOT_FOUND",
                "Account Not Found",
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI(),
                "ACCOUNT_NOT_FOUND");
    }



    @ExceptionHandler(ClosureRequestNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleClosureRequestNotFound(
            ClosureRequestNotFoundException ex, HttpServletRequest request) {
        log.warn("Closure request not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse("https://api.corebank.local/errors/CLOSURE_REQUEST_NOT_FOUND",
                "Closure Request Not Found",
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI(),
                "CLOSURE_REQUEST_NOT_FOUND");
    }



    @ExceptionHandler(AccountNonZeroBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNonZeroBalance(
            AccountNonZeroBalanceException ex, HttpServletRequest request) {
        log.warn("Account non-zero balance on {}: {}", request.getRequestURI(), ex.getMessage());
        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/ACCOUNT_NON_ZERO_BALANCE",
                "Account Balance Non-Zero",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "ACCOUNT_NON_ZERO_BALANCE"
        );
        if (ex.getAccountNumber() != null) {
            problem.put("accountNumber", ex.getAccountNumber());
        }
        if (ex.getBalance() != null) {
            problem.put("availableBalance", ex.getBalance());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccountActiveFlagException.class)
    public ResponseEntity<Map<String, Object>> handleAccountActiveFlag(
            AccountActiveFlagException ex, HttpServletRequest request) {
        log.warn("Account active flag on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse("https://api.corebank.local/errors/ACCOUNT_ACTIVE_FLAG_EXISTS",
                "Active Hold Exists",
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage(),
                request.getRequestURI(),
                "ACCOUNT_ACTIVE_FLAG_EXISTS");
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotActive(
            AccountNotActiveException ex, HttpServletRequest request) {
        log.warn("Account not active on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse("https://api.corebank.local/errors/ACCOUNT_NOT_ACTIVE",
                "Account Not Active",
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage(),
                request.getRequestURI(),
                "ACCOUNT_NOT_ACTIVE");
    }

    @ExceptionHandler(DuplicateClosureRequestException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateClosureRequest(
            DuplicateClosureRequestException ex, HttpServletRequest request) {
        log.warn("Duplicate closure request on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse("https://api.corebank.local/errors/DUPLICATE_CLOSURE_REQUEST",
                "Duplicate Closure Request",
                HttpStatus.CONFLICT,
                ex.getMessage(),
                request.getRequestURI(),
                "DUPLICATE_CLOSURE_REQUEST");
    }

    @ExceptionHandler(UnauthorizedAccountAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAccountAccess(
            UnauthorizedAccountAccessException ex, HttpServletRequest request) {
        log.warn("Unauthorized account access on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse("https://api.corebank.local/errors/AUTH_ACCESS_DENIED",
                "Access Denied",
                HttpStatus.FORBIDDEN,
                ex.getMessage(),
                request.getRequestURI(),
                "AUTH_ACCESS_DENIED");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse("https://api.corebank.local/errors/AUTH_ACCESS_DENIED",
                "Access Denied",
                HttpStatus.FORBIDDEN,
                "You do not possess the required security role to execute this operation.",
                request.getRequestURI(),
                "AUTH_ACCESS_DENIED");
    }

    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<Map<String, Object>> handleTokenRevoked(
            TokenRevokedException ex, HttpServletRequest request) {
        log.warn("Token revoked on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse("https://api.corebank.local/errors/AUTH_TOKEN_REVOKED",
                "Token Revoked",
                HttpStatus.UNAUTHORIZED,
                ex.getMessage(),
                request.getRequestURI(),
                "AUTH_TOKEN_REVOKED");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation error on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/VALIDATION_FAILED",
                "Validation Failed",
                HttpStatus.BAD_REQUEST.value(),
                "Request content contains invalid parameters.",
                request.getRequestURI(),
                "VALIDATION_FAILED"
        );

        List<Map<String, String>> invalidParams = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> param = new LinkedHashMap<>();
            param.put("name", error.getField());
            param.put("reason", error.getDefaultMessage());
            invalidParams.add(param);
        }
        problem.put("invalidParams", invalidParams);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse("https://api.corebank.local/errors/BAD_REQUEST",
                "Bad Request",
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI(),
                "BAD_REQUEST");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        log.warn("Illegal state on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse("https://api.corebank.local/errors/ILLEGAL_STATE",
                "Illegal State",
                HttpStatus.CONFLICT,
                ex.getMessage(),
                request.getRequestURI(),
                "ILLEGAL_STATE");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal server error on {}: ", request.getRequestURI(), ex);
        return buildResponse("https://api.corebank.local/errors/INTERNAL_SERVER_ERROR",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred. Please contact system support.",
                request.getRequestURI(),
                "INTERNAL_SERVER_ERROR");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            String type, String title, HttpStatus status, String detail, String instance, String errorCode) {
        Map<String, Object> problem = createProblemDetails(type, title, status.value(), detail, instance, errorCode);
        return ResponseEntity
                .status(status)
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
        problem.put("invalidParams", List.of());
        return problem;
    }
}
