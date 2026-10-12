package com.group2.fse.transaction_service.interbank.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.transaction_service.interbank.security.InterbankSecurityProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuraBankPartnerAdapter: AuraBank OpenAPI inward wire dispatch and response parsing")
class AuraBankPartnerAdapterTest {

    private static final UUID MESSAGE_ID = UUID.fromString("0b6c2f6e-6d1a-4d5c-9d1f-0f3a5e7b9c21");
    private static final String API_KEY = "AURABANK-SECURE-KEY-2026";

    private record Seen(String method, String path, String apiKey, String interbankKey, String authHeader, String body) {}
    private record Reply(int status, String body) {}

    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    private final List<Reply> replies = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;
    private AuraBankPartnerAdapter adapter;
    private InterbankSecurityProperties.PartnerConfig auraBank;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.add(new Seen(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-API-Key"),
                    exchange.getRequestHeaders().getFirst("X-Interbank-API-Key"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    body
            ));
            Reply reply = replies.get(Math.min(seen.size(), replies.size()) - 1);
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        auraBank = new InterbankSecurityProperties.PartnerConfig();
        auraBank.setBankCode("AURABANK");
        auraBank.setSharedSecret(API_KEY);
        auraBank.setBaseUrl("http://localhost:" + server.getAddress().getPort());

        adapter = new AuraBankPartnerAdapter(objectMapper, 2000);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private OutboundTransfer sampleTransfer() {
        return new OutboundTransfer(
                MESSAGE_ID,
                auraBank,
                "COOBS",
                "ACC_10000001",
                "1000-2000-3001",
                "Alice Guo",
                new BigDecimal("1000.00"),
                "PHP",
                "Consulting fees settlement",
                "TX-20261012-8F3A21C0",
                Instant.parse("2026-10-12T17:30:00Z")
        );
    }

    @Test
    @DisplayName("send() dispatches to /api/v1/interbank/transfers/inward with API keys and parses COMPLETED response")
    void sendSuccessfulWire() {
        replies.add(new Reply(200, """
                {
                  "status": "COMPLETED",
                  "referenceNumber": "INW-0b6c2f6e-6d1a-4d5c-9d1f-0f3a5e7b9c21",
                  "timestamp": "2026-10-12T17:30:00.000Z",
                  "settlementAmount": 1000.0,
                  "currency": "PHP"
                }
                """));

        PartnerAnswer answer = adapter.send(sampleTransfer());

        assertThat(seen).hasSize(1);
        Seen request = seen.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/v1/interbank/transfers/inward");
        assertThat(request.apiKey()).isEqualTo(API_KEY);
        assertThat(request.interbankKey()).isEqualTo(API_KEY);
        assertThat(request.authHeader()).isEqualTo("Bearer " + API_KEY);
        assertThat(request.body()).contains("\"destinationBankCode\":\"AURABANK\"");
        assertThat(request.body()).contains("\"amount\":1000.0");

        assertThat(answer.outcome()).isEqualTo(PartnerAnswer.Outcome.ACCEPTED);
        assertThat(answer.reference()).isEqualTo("INW-0b6c2f6e-6d1a-4d5c-9d1f-0f3a5e7b9c21");
    }

    @Test
    @DisplayName("send() parses 422 Unprocessable Entity error response")
    void sendAccountFrozenError() {
        replies.add(new Reply(422, """
                {
                  "status": 422,
                  "error": "Unprocessable Entity",
                  "message": "Beneficiary account is frozen / suspended"
                }
                """));

        PartnerAnswer answer = adapter.send(sampleTransfer());

        assertThat(answer.outcome()).isEqualTo(PartnerAnswer.Outcome.REJECTED);
        assertThat(answer.reasonCode()).isEqualTo("Beneficiary account is frozen / suspended");
    }

    @Test
    @DisplayName("send() parses 404 Not Found error response")
    void sendAccountNotFoundError() {
        replies.add(new Reply(404, """
                {
                  "status": 404,
                  "error": "Not Found",
                  "message": "Beneficiary account 1000-2000-9999 not found"
                }
                """));

        PartnerAnswer answer = adapter.send(sampleTransfer());

        assertThat(answer.outcome()).isEqualTo(PartnerAnswer.Outcome.REJECTED);
        assertThat(answer.reasonCode()).contains("not found");
    }
}
