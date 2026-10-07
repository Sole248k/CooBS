package com.group2.fse.ledger_service.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/ledger/transfers");
    }

    @Test
    void shouldHandleInsufficientFundsException() {
        InsufficientFundsException ex = new InsufficientFundsException(
                10L, new BigDecimal("100.0000"), new BigDecimal("500.0000"));

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInsufficientFunds(ex, request);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INSUFFICIENT_FUNDS", body.get("errorCode"));
        assertEquals(422, body.get("status"));
        assertEquals("/api/v1/ledger/transfers", body.get("instance"));
        assertEquals(10L, body.get("accountId"));
        assertEquals(new BigDecimal("100.0000"), body.get("currentBalance"));
        assertEquals(new BigDecimal("500.0000"), body.get("attemptedAmount"));
    }

    @Test
    void shouldHandleAccountNotFoundException() {
        AccountNotFoundException ex = new AccountNotFoundException("Account 999 was not found");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAccountNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ACCOUNT_NOT_FOUND", body.get("errorCode"));
        assertEquals(404, body.get("status"));
    }

    @Test
    void shouldHandleDuplicateIdempotencyKeyException() {
        DuplicateIdempotencyKeyException ex = new DuplicateIdempotencyKeyException("Duplicate key");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleDuplicateIdempotencyKey(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("DUPLICATE_IDEMPOTENCY_KEY", body.get("errorCode"));
        assertEquals(409, body.get("status"));
    }

    @Test
    void shouldHandleInvalidTransactionException() {
        InvalidTransactionException ex = new InvalidTransactionException("Source and destination cannot be identical");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleInvalidTransaction(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INVALID_TRANSACTION", body.get("errorCode"));
        assertEquals(400, body.get("status"));
    }

    @Test
    void shouldHandleMethodArgumentNotValidException() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "requestDto");
        bindingResult.addError(new FieldError("requestDto", "amount", "Amount must be strictly positive"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleMethodArgumentNotValid(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INVALID_REQUEST_PARAMETERS", body.get("errorCode"));
        assertTrue(body.containsKey("invalidParams"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> invalidParams = (List<Map<String, String>>) body.get("invalidParams");
        assertEquals(1, invalidParams.size());
        assertEquals("amount", invalidParams.get(0).get("field"));
    }

    @Test
    void shouldHandleConcurrencyFailureException() {
        CannotAcquireLockException ex = new CannotAcquireLockException("ORA-00054: resource busy");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleConcurrencyFailure(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("CONCURRENT_TRANSACTION_IN_FLIGHT", body.get("errorCode"));
        assertEquals(409, body.get("status"));
    }

    @Test
    void shouldHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Insufficient role");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("AUTH_ACCESS_DENIED", body.get("errorCode"));
        assertEquals(403, body.get("status"));
    }

    @Test
    void shouldHandleAccountNotActiveException() {
        AccountNotActiveException ex = new AccountNotActiveException(50L, "FROZEN");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAccountNotActive(ex, request);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ACCOUNT_NOT_ACTIVE", body.get("errorCode"));
        assertEquals(422, body.get("status"));
        assertEquals(50L, body.get("accountId"));
        assertEquals("FROZEN", body.get("accountStatus"));
    }

    @Test
    void shouldHandleAccountNonZeroBalanceException() {
        AccountNonZeroBalanceException ex = new AccountNonZeroBalanceException(
                "ACC_10000004", new BigDecimal("12500.0000"));

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAccountNonZeroBalance(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ACCOUNT_NON_ZERO_BALANCE", body.get("errorCode"));
        assertEquals(400, body.get("status"));
        assertEquals("ACC_10000004", body.get("accountNumber"));
        assertEquals(new BigDecimal("12500.0000"), body.get("availableBalance"));
    }

    @Test
    void shouldHandleAccountActiveFlagExistsException() {
        AccountActiveFlagExistsException ex = new AccountActiveFlagExistsException(88L, "AML_RISK_HOLD");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAccountActiveFlagExists(ex, request);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("ACCOUNT_ACTIVE_FLAG_EXISTS", body.get("errorCode"));
        assertEquals(422, body.get("status"));
        assertEquals(88L, body.get("accountId"));
        assertEquals("AML_RISK_HOLD", body.get("flagType"));
    }

    @Test
    void shouldHandleDualWriteAuditException() {
        DualWriteAuditException ex = new DualWriteAuditException("PostgreSQL connection refused");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleDualWriteAuditException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("LEDGER_AUDIT_DUAL_WRITE_FAILED", body.get("errorCode"));
        assertEquals("AUDIT_LOGGING_FAILURE", body.get("legacyErrorCode"));
        assertEquals(500, body.get("status"));
    }

    @Test
    void shouldHandleGeneralException() {
        RuntimeException ex = new RuntimeException("Unexpected runtime error");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGeneralException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_SERVER_ERROR", body.get("errorCode"));
        assertEquals(500, body.get("status"));
    }
}
