package com.group2.fse.orchestration_service.client;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ledger-service internal API (H3): L3 hold, L4 capture, L5 release, L8 postings.
 * Every call carries Idempotency-Key {@code <transactionId>:<step>} with a fixed step name,
 * so a retry or a resumed saga is the same request to the ledger, never a second one.
 */
@Component
public class LedgerClient {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final InternalApi api;
    private final RestClient http;

    public LedgerClient(InternalApi api,
            @Value("${app.clients.ledger.base-url}") String baseUrl,
            @Value("${app.clients.ledger.timeout}") Duration timeout) {
        this.api = api;
        this.http = api.client(baseUrl, timeout);
    }

    public Hold placeHold(HoldRequest request) {
        return api.call(() -> http.post()
                .uri("/internal/v1/ledger/holds")
                .header(IDEMPOTENCY_HEADER, request.transactionId() + ":hold")
                .body(request)
                .retrieve()
                .body(Hold.class));
    }

    public Hold capture(UUID transactionId, Long holdId, CaptureRequest request) {
        return api.call(() -> http.post()
                .uri("/internal/v1/ledger/holds/{holdId}/capture", holdId)
                .header(IDEMPOTENCY_HEADER, transactionId + ":capture")
                .body(request)
                .retrieve()
                .body(Hold.class));
    }

    public Hold release(UUID transactionId, Long holdId, String reason) {
        return api.call(() -> http.post()
                .uri("/internal/v1/ledger/holds/{holdId}/release", holdId)
                .header(IDEMPOTENCY_HEADER, transactionId + ":release")
                .body(new ReleaseRequest(reason))
                .retrieve()
                .body(Hold.class));
    }

    public PostingsResult post(PostingsRequest request) {
        return api.call(() -> http.post()
                .uri("/internal/v1/ledger/postings")
                .header(IDEMPOTENCY_HEADER, request.transactionId() + ":posting")
                .body(request)
                .retrieve()
                .body(PostingsResult.class));
    }

    public record HoldRequest(
            UUID transactionId,
            String referenceNo,
            Long accountId,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            String currency,
            Long actorId) {
    }

    public record CaptureRequest(Long destinationAccountId, Long actorId) {
    }

    public record ReleaseRequest(String reason) {
    }

    /** The part of the L3, L4 and L5 responses the saga uses. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hold(Long holdId, String status) {
    }

    public record PostingsRequest(UUID transactionId, List<Posting> postings) {
    }

    public record Posting(
            String referenceNo,
            Long debitAccountId,
            Long creditAccountId,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            String remarks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostingsResult(int posted, int failed, List<PostingResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostingResult(String referenceNo, String status, String errorCode) {
    }
}
