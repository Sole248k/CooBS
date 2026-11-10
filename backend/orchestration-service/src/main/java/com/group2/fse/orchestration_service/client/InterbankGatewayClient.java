package com.group2.fse.orchestration_service.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The inter-bank gateway in transaction-service, I4. The saga decides when a transfer goes
 * to another bank; the gateway is the only code that talks to that bank. It holds the
 * partner's address and shared secret, builds and signs the partner's message, and asks the
 * partner's status endpoint when the first answer does not arrive.
 *
 * The call is safe to repeat: the gateway sends one message per transactionId, however often
 * it is asked, and a repeat answers with what the partner said the first time.
 */
@Component
public class InterbankGatewayClient {

    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final InternalApi api;
    private final RestClient http;

    public InterbankGatewayClient(InternalApi api,
            @Value("${app.clients.interbank.base-url}") String baseUrl,
            @Value("${app.clients.interbank.timeout}") Duration timeout) {
        this.api = api;
        this.http = api.client(baseUrl, timeout);
    }

    /**
     * Sends the transfer to the partner bank, or asks what became of it if it was sent before.
     * A DownstreamException that is a refusal means the gateway sent nothing; one that is not
     * means the outcome is unknown.
     */
    public Answer send(Dispatch request) {
        return api.call(() -> http.post()
                .uri("/internal/v1/interbank/outbound")
                .header(IDEMPOTENCY_HEADER, request.transactionId() + ":interbank")
                .body(request)
                .retrieve()
                .body(Answer.class));
    }

    public record Dispatch(
            UUID transactionId,
            String referenceNo,
            String destinationBankCode,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            String currency) {
    }

    /**
     * partnerStatus is ACCEPTED, REJECTED or PENDING. PENDING is not an answer from the
     * partner: it says the gateway asked and still does not know.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(String partnerStatus, String partnerReference, String reasonCode) {

        public boolean accepted() {
            return ACCEPTED.equals(partnerStatus);
        }

        public boolean rejected() {
            return REJECTED.equals(partnerStatus);
        }
    }
}
