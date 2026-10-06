package com.group2.fse.ledger_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import com.group2.fse.ledger_service.security.blacklist.TokenRevokedException;
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

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest request) {
        log.warn("Insufficient funds exception on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/INSUFFICIENT_FUNDS",
                "Insufficient Funds",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "INSUFFICIENT_FUNDS"
        );
        if (ex.getAccountId() != null) {
            problem.put("accountId", ex.getAccountId());
        }
        if (ex.getCurrentBalance() != null) {
            problem.put("currentBalance", ex.getCurrentBalance());
        }
        if (ex.getAttemptedAmount() != null) {
            problem.put("attemptedAmount", ex.getAttemptedAmount());
        }

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotFound(
            AccountNotFoundException ex, HttpServletRequest request) {
        log.warn("Account not found exception on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/ACCOUNT_NOT_FOUND",
                "Account Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "ACCOUNT_NOT_FOUND"
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionNotFound(
            TransactionNotFoundException ex, HttpServletRequest request) {
        log.warn("Transaction not found exception on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/TRANSACTION_NOT_FOUND",
                "Transaction Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "TRANSACTION_NOT_FOUND"
        );
        if (ex.getReferenceNo() != null) {
            problem.put("referenceNo", ex.getReferenceNo());
        }

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(TransactionAlreadyReversedException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionAlreadyReversed(
            TransactionAlreadyReversedException ex, HttpServletRequest request) {
        log.warn("Transaction already reversed on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/ALREADY_REVERSED",
                "Transaction Already Reversed",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "ALREADY_REVERSED"
        );
        if (ex.getReferenceNo() != null) {
            problem.put("referenceNo", ex.getReferenceNo());
        }

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(DuplicateIdempotencyKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateIdempotencyKey(
            DuplicateIdempotencyKeyException ex, HttpServletRequest request) {
        log.warn("Duplicate idempotency key exception on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/DUPLICATE_IDEMPOTENCY_KEY",
                "Duplicate Idempotency Key",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "DUPLICATE_IDEMPOTENCY_KEY"
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler({InvalidTransactionException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidTransaction(
            RuntimeException ex, HttpServletRequest request) {
        log.warn("Invalid transaction parameters on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/INVALID_TRANSACTION",
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
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
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
                "https://api.corebank.local/errors/INVALID_REQUEST_PARAMETERS",
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

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrencyFailure(
            ConcurrencyFailureException ex, HttpServletRequest request) {
        log.warn("Concurrency failure on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/CONCURRENT_TRANSACTION_IN_FLIGHT",
                "Concurrent Transaction Conflict",
                HttpStatus.CONFLICT.value(),
                "Could not acquire row lock due to concurrent transaction in flight or lock timeout. Please retry.",
                request.getRequestURI(),
                "CONCURRENT_TRANSACTION_IN_FLIGHT"
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(TokenRevokedException.class)
    public ResponseEntity<Map<String, Object>> handleTokenRevoked(
            TokenRevokedException ex, HttpServletRequest request) {
        log.warn("Token revoked on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/AUTH_TOKEN_REVOKED",
                "Token Revoked",
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage() != null ? ex.getMessage() : "This session was terminated upon logout. Please authenticate with new credentials.",
                request.getRequestURI(),
                "AUTH_TOKEN_REVOKED"
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/AUTH_ACCESS_DENIED",
                "Forbidden",
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage() != null ? ex.getMessage() : "You do not have permission to access this resource.",
                request.getRequestURI(),
                "AUTH_ACCESS_DENIED"
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotActive(
            AccountNotActiveException ex, HttpServletRequest request) {
        log.warn("Account not active exception on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/ACCOUNT_NOT_ACTIVE",
                "Account Not Active",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "ACCOUNT_NOT_ACTIVE"
        );
        if (ex.getAccountId() != null) {
            problem.put("accountId", ex.getAccountId());
        }
        if (ex.getAccountStatus() != null) {
            problem.put("accountStatus", ex.getAccountStatus());
        }

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccountNonZeroBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNonZeroBalance(
            AccountNonZeroBalanceException ex, HttpServletRequest request) {
        log.warn("Account non-zero balance on closure on {}: {}", request.getRequestURI(), ex.getMessage());

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
        if (ex.getAvailableBalance() != null) {
            problem.put("availableBalance", ex.getAvailableBalance());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccountActiveFlagExistsException.class)
    public ResponseEntity<Map<String, Object>> handleAccountActiveFlagExists(
            AccountActiveFlagExistsException ex, HttpServletRequest request) {
        log.warn("Account active flag block on {}: {}", request.getRequestURI(), ex.getMessage());

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/ACCOUNT_ACTIVE_FLAG_EXISTS",
                "Account Flag Restricted",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI(),
                "ACCOUNT_ACTIVE_FLAG_EXISTS"
        );
        if (ex.getAccountId() != null) {
            problem.put("accountId", ex.getAccountId());
        }
        if (ex.getFlagType() != null) {
            problem.put("flagType", ex.getFlagType());
        }

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(DualWriteAuditException.class)
    public ResponseEntity<Map<String, Object>> handleDualWriteAuditException(
            DualWriteAuditException ex, HttpServletRequest request) {
        log.error("Dual-write audit failure on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/LEDGER_AUDIT_DUAL_WRITE_FAILED",
                "Dual-Write Audit Logging Failure",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Forensic audit logging failed; transaction was rolled back for safety.",
                request.getRequestURI(),
                "LEDGER_AUDIT_DUAL_WRITE_FAILED"
        );
        problem.put("legacyErrorCode", "AUDIT_LOGGING_FAILURE");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        Map<String, Object> problem = createProblemDetails(
                "https://api.corebank.local/errors/INTERNAL_SERVER_ERROR",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected internal error occurred. Please contact system support.",
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
