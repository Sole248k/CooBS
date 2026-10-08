package com.group2.fse.transaction_service.client;

import com.group2.fse.transaction_service.client.dto.AccountLookupResponse;
import com.group2.fse.transaction_service.client.dto.StartReversalSagaRequest;
import com.group2.fse.transaction_service.client.dto.StartTransferSagaRequest;
import com.group2.fse.transaction_service.security.jwt.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Client for triggering orchestration-service (O1: POST /internal/v1/sagas/transfers)
 * and querying account-service (K6/K7: GET /internal/v1/accounts/**).
 * Automatically injects internal service bearer token signed by app.security.jwt.secret.
 */
@Slf4j
@Component
public class OrchestrationClient {

    private final RestClient orchestrationRestClient;
    private final RestClient accountRestClient;
    private final JwtTokenProvider jwtTokenProvider;

    public OrchestrationClient(
            @Value("${app.clients.orchestration.base-url:${ORCHESTRATION_SERVICE_URL:http://localhost:8091}}") String orchestrationBaseUrl,
            @Value("${app.clients.account.base-url:${ACCOUNT_SERVICE_URL:http://localhost:8082}}") String accountBaseUrl,
            @Value("${app.clients.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${app.clients.read-timeout-ms:3000}") int readTimeout,
            JwtTokenProvider jwtTokenProvider) {

        this.jwtTokenProvider = jwtTokenProvider;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeout));

        this.orchestrationRestClient = RestClient.builder()
                .baseUrl(orchestrationBaseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    log.error("Orchestration service call failed: URI={}, status={}", request.getURI(), response.getStatusCode());
                })
                .build();

        this.accountRestClient = RestClient.builder()
                .baseUrl(accountBaseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    log.warn("Account service lookup call returned error: URI={}, status={}", request.getURI(), response.getStatusCode());
                })
                .build();
    }

    /** K7. Empty when account-service says the account does not exist. */
    public Optional<AccountLookupResponse> findAccountByNumber(String accountNumber) {
        return findAccount("/internal/v1/accounts/by-number/{accountNumber}", accountNumber);
    }

    /** K6. Empty when account-service says the account does not exist. */
    public Optional<AccountLookupResponse> findAccountById(Long accountId) {
        return findAccount("/internal/v1/accounts/{accountId}", accountId);
    }

    /**
     * A 404 is an answer: the account does not exist. Anything else that is not a 2xx, and any
     * failure to reach account-service, is no answer, and the caller must not guess.
     *
     * @throws AccountLookupUnavailableException when account-service gave no usable answer
     */
    private Optional<AccountLookupResponse> findAccount(String uriTemplate, Object key) {
        try {
            return accountRestClient.get()
                    .uri(uriTemplate, key)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getServiceToken())
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 404) {
                            return Optional.<AccountLookupResponse>empty();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new AccountLookupUnavailableException(
                                    "account-service answered " + response.getStatusCode().value(), null);
                        }
                        AccountLookupResponse body = response.bodyTo(AccountLookupResponse.class);
                        return body == null || body.getAccountId() == null
                                ? Optional.<AccountLookupResponse>empty() : Optional.of(body);
                    });
        } catch (AccountLookupUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AccountLookupUnavailableException("account-service could not be reached: " + ex.getMessage(), ex);
        }
    }

    /**
     * Asynchronously dispatches StartTransferRequest to orchestration-service O1: POST /internal/v1/sagas/transfers.
     * Non-blocking to guarantee sub-200ms 202 Accepted response times at the transaction ingress shock absorber.
     */
    @Async
    public void triggerTransferSagaAsync(StartTransferSagaRequest request, String correlationId) {
        triggerTransferSaga(request, correlationId);
    }

    /**
     * Synchronously dispatches StartTransferRequest to orchestration-service O1.
     */
    public boolean triggerTransferSaga(StartTransferSagaRequest request, String correlationId) {
        try {
            String token = getServiceToken();
            log.info("Triggering orchestration saga O1 for txId={}, ref={}", request.getTransactionId(), request.getReferenceNo());

            var requestSpec = orchestrationRestClient.post()
                    .uri("/internal/v1/sagas/transfers")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(request);

            if (correlationId != null && !correlationId.isBlank()) {
                requestSpec.header("X-Correlation-ID", correlationId);
            }

            var responseEntity = requestSpec.retrieve().toBodilessEntity();
            log.info("Orchestration saga initiated successfully for txId={}, statusCode={}",
                    request.getTransactionId(), responseEntity.getStatusCode());
            return responseEntity.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.error("Failed to trigger orchestration saga for txId={}: {}", request.getTransactionId(), ex.getMessage());
            return false;
        }
    }

    /** O2: POST /internal/v1/sagas/reversals, sent in the background. Idempotent on the transaction ID. */
    @Async
    public void triggerReversalSagaAsync(StartReversalSagaRequest request, String correlationId) {
        try {
            log.info("Triggering reversal saga O2 for txId={}, reverses={}",
                    request.getTransactionId(), request.getReversesTransactionId());
            var requestSpec = orchestrationRestClient.post()
                    .uri("/internal/v1/sagas/reversals")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getServiceToken())
                    .body(request);
            if (correlationId != null && !correlationId.isBlank()) {
                requestSpec.header("X-Correlation-ID", correlationId);
            }
            var responseEntity = requestSpec.retrieve().toBodilessEntity();
            log.info("Reversal saga start for txId={} answered {}", request.getTransactionId(), responseEntity.getStatusCode());
        } catch (Exception ex) {
            log.error("Failed to trigger reversal saga for txId={}: {}", request.getTransactionId(), ex.getMessage());
        }
    }

    private String getServiceToken() {
        return jwtTokenProvider.generateToken(0L, "transaction-service", List.of("ROLE_SERVICE"));
    }
}
