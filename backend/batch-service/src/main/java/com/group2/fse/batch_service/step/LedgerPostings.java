package com.group2.fse.batch_service.step;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.group2.fse.batch_service.security.jwt.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L8, the ledger's postings endpoint. Every end-of-day posting goes through it, so the ledger
 * locks the two accounts, checks the funds, writes the journal and the audit row, and keeps
 * both balances right. This service never writes a balance itself.
 *
 * A posting is recognised by its reference: sending the same one again is reported POSTED and
 * not posted twice, so a step that is run again is safe.
 */
@Slf4j
@Component
public class LedgerPostings {

    /** The ledger's code for a reference that was already used for another amount or account. */
    public static final String ALREADY_USED = "DUPLICATE_IDEMPOTENCY_KEY";
    private static final int MAX_PER_REQUEST = 500;

    private final RestClient http;
    private final JwtTokenProvider tokens;

    public LedgerPostings(@Value("${ledger.service.url}") String baseUrl,
                          @Value("${ledger.service.timeout:10s}") Duration timeout,
                          JwtTokenProvider tokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(timeout);
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.tokens = tokens;
    }

    public record Posting(
            String referenceNo,
            Long debitAccountId,
            Long creditAccountId,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            String remarks) {
    }

    private record Request(Long batchRunId, String stepName, List<Posting> postings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Response(List<Result> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Result(String referenceNo, String status, String errorCode) {
    }

    /**
     * Posts the list and says what became of each reference: null when it is in the journal,
     * otherwise the ledger's error code for the refusal.
     *
     * @throws RuntimeException when the ledger gives no answer. The outcome is then unknown, and
     *         the step must fail instead of guessing; running it again repeats the same references.
     */
    public Map<String, String> post(Long batchRunId, String stepName, List<Posting> postings) {
        Map<String, String> outcomes = new HashMap<>();
        for (int from = 0; from < postings.size(); from += MAX_PER_REQUEST) {
            List<Posting> part = postings.subList(from, Math.min(from + MAX_PER_REQUEST, postings.size()));
            Response response = http.post()
                    .uri("/internal/v1/ledger/postings")
                    .header("Authorization", "Bearer " + tokens.generateToken(0L, "batch-service", List.of("ROLE_SERVICE")))
                    .header("Idempotency-Key", batchRunId + ":" + stepName + ":" + from)
                    .header("X-Correlation-ID", UUID.randomUUID().toString())
                    .body(new Request(batchRunId, stepName, part))
                    .retrieve()
                    .body(Response.class);
            if (response == null || response.results() == null || response.results().size() != part.size()) {
                throw new IllegalStateException("The ledger's answer to " + stepName + " does not cover every posting");
            }
            for (Result result : response.results()) {
                outcomes.put(result.referenceNo(), "POSTED".equals(result.status()) ? null
                        : result.errorCode() != null ? result.errorCode() : "INTERNAL_SERVER_ERROR");
            }
        }
        return outcomes;
    }
}
