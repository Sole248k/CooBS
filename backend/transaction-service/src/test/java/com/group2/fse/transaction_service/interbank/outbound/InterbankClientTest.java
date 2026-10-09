package com.group2.fse.transaction_service.interbank.outbound;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.transaction_service.interbank.security.InterbankAuthValidator;
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
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The partner is a stub HTTP server, so the signature, the headers and the status codes go over
 * a real socket, and the stub checks the signature with the same validator we use for I1.
 */
@DisplayName("Sending to a partner bank: signed as the proposal says, asked for status when silent, repeated at most once")
class InterbankClientTest {

    private static final UUID MESSAGE = UUID.fromString("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11");
    private static final String SECRET = "test-shared-secret-2026";

    private record Seen(String method, String path, String bankCode, String timestamp, String signature, String body) {
    }

    private record Reply(int status, String body) {
    }

    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    /** Replies in order; the last one repeats. */
    private final List<Reply> replies = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private HttpServer partner;
    private InterbankClient client;
    private InterbankSecurityProperties.PartnerConfig bankB;

    @BeforeEach
    void startPartner() throws IOException {
        partner = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        partner.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-Bank-Code"),
                    exchange.getRequestHeaders().getFirst("X-Timestamp"),
                    exchange.getRequestHeaders().getFirst("X-Signature"), body));
            Reply reply = replies.get(Math.min(seen.size(), replies.size()) - 1);
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        partner.start();

        bankB = new InterbankSecurityProperties.PartnerConfig();
        bankB.setBankCode("BANKB");
        bankB.setSharedSecret(SECRET);
        bankB.setBaseUrl("http://localhost:" + partner.getAddress().getPort());
        client = new InterbankClient(new ProposalPartnerAdapter(objectMapper, 2000), new LoopbackPartnerAdapter(), 3, 0);
    }

    @AfterEach
    void stopPartner() {
        partner.stop(0);
    }

    private OutboundTransfer transfer() {
        return new OutboundTransfer(MESSAGE, bankB, "COOBS", "ACC_10000001", "1000-2000-3000", "Ana Lim",
                new BigDecimal("2500.0000"), "PHP", "Padala sa kapatid", "TX-20261003-8F3A21C0",
                Instant.parse("2026-10-03T11:40:00Z"));
    }

    private static Reply accepted() {
        return new Reply(200, """
                {"messageId":"6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11","status":"ACCEPTED",
                 "receiverReference":"BB-20261003-000123","beneficiaryName":"A** L**","somethingNew":true}""");
    }

    private static Reply notFound() {
        return new Reply(404, """
                {"messageId":"6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11","status":"NOT_FOUND"}""");
    }

    private static Reply silent() {
        return new Reply(503, "{}");
    }

    @Test
    @DisplayName("the message is the proposal's, and the partner can verify its signature with the shared secret")
    void sendsASignedProposalMessage() throws Exception {
        replies.add(accepted());

        PartnerAnswer answer = client.deliver(transfer(), false);

        assertThat(answer.outcome()).isEqualTo(PartnerAnswer.Outcome.ACCEPTED);
        assertThat(answer.reference()).isEqualTo("BB-20261003-000123");
        assertThat(seen).hasSize(1);
        Seen request = seen.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/v1/interbank/transfers");
        assertThat(request.bankCode()).isEqualTo("COOBS");

        JsonNode body = objectMapper.readTree(request.body());
        assertThat(body.path("messageId").asText()).isEqualTo(MESSAGE.toString());
        assertThat(body.path("sourceBankCode").asText()).isEqualTo("COOBS");
        assertThat(body.path("destinationBankCode").asText()).isEqualTo("BANKB");
        assertThat(body.path("sender").path("accountNumber").asText()).isEqualTo("ACC_10000001");
        assertThat(body.path("beneficiary").path("accountNumber").asText()).isEqualTo("1000-2000-3000");
        assertThat(body.path("beneficiary").path("name").asText()).isEqualTo("Ana Lim");
        assertThat(body.path("amount").asText()).isEqualTo("2500.00");
        assertThat(body.path("senderReference").asText()).isEqualTo("TX-20261003-8F3A21C0");

        // What the partner would do with it: the validator we run on I1, with us as the sending bank.
        InterbankSecurityProperties theirs = new InterbankSecurityProperties();
        InterbankSecurityProperties.PartnerConfig us = new InterbankSecurityProperties.PartnerConfig();
        us.setBankCode("COOBS");
        us.setSharedSecret(SECRET);
        theirs.getPartners().put("COOBS", us);
        assertThatCode(() -> new InterbankAuthValidator(theirs).validate(request.bankCode(), request.timestamp(),
                request.signature(), null, request.body())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a refusal is an answer, whatever HTTP status carries it")
    void rejectionIsAnAnswer() {
        replies.add(new Reply(422, """
                {"messageId":"x","status":"REJECTED","reasonCode":"ACCOUNT_NOT_FOUND","reasonMessage":"No account."}"""));

        PartnerAnswer answer = client.deliver(transfer(), false);

        assertThat(answer.outcome()).isEqualTo(PartnerAnswer.Outcome.REJECTED);
        assertThat(answer.reasonCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(seen).hasSize(1);
    }

    @Test
    @DisplayName("no answer to the send: the status endpoint is asked, and its answer is the answer")
    void silenceThenStatus() {
        replies.add(silent());
        replies.add(silent());
        replies.add(accepted());

        PartnerAnswer answer = client.deliver(transfer(), false);

        assertThat(answer.outcome()).isEqualTo(PartnerAnswer.Outcome.ACCEPTED);
        assertThat(seen).extracting(Seen::method).containsExactly("POST", "GET", "GET");
        assertThat(seen.get(1).path()).isEqualTo("/api/v1/interbank/transfers/" + MESSAGE);
        // A status request has no body; it is signed over the timestamp alone.
        assertThat(seen.get(1).signature()).isEqualTo(
                InterbankAuthValidator.computeHmacSha256Hex(SECRET, seen.get(1).timestamp() + "."));
    }

    @Test
    @DisplayName("status says it never arrived: the same message is sent once more, and only once")
    void notFoundAllowsOneRepeat() {
        replies.add(silent());
        replies.add(notFound());
        replies.add(silent());
        replies.add(notFound());

        PartnerAnswer answer = client.deliver(transfer(), false);

        assertThat(answer.outcome()).isEqualTo(PartnerAnswer.Outcome.UNKNOWN);
        assertThat(seen.stream().filter(request -> request.method().equals("POST"))).hasSize(2);
        assertThat(seen.stream().filter(request -> request.method().equals("POST")).map(Seen::body).distinct())
                .as("the repeat is the same message").hasSize(1);
    }

    @Test
    @DisplayName("a partner that stays silent is asked for status three times, then the outcome is unknown")
    void silenceThroughout() {
        replies.add(silent());

        assertThat(client.deliver(transfer(), false).outcome()).isEqualTo(PartnerAnswer.Outcome.UNKNOWN);
        assertThat(seen).extracting(Seen::method).containsExactly("POST", "GET", "GET", "GET");
    }

    @Test
    @DisplayName("a message that may have been sent is asked about before anything is sent")
    void possiblySentIsAskedFirst() {
        replies.add(accepted());

        assertThat(client.deliver(transfer(), true).outcome()).isEqualTo(PartnerAnswer.Outcome.ACCEPTED);
        assertThat(seen).extracting(Seen::method).containsExactly("GET");

        seen.clear();
        replies.clear();
        replies.add(silent());
        // Still unknown at the partner: nothing is sent on a guess.
        assertThat(client.deliver(transfer(), true).outcome()).isEqualTo(PartnerAnswer.Outcome.UNKNOWN);
        assertThat(seen).extracting(Seen::method).containsExactly("GET");
    }

    @Test
    @DisplayName("a message that may have been sent but never arrived is sent again; without the sender's account it is not")
    void possiblySentButNeverArrived() {
        replies.add(notFound());
        replies.add(accepted());

        assertThat(client.deliver(transfer(), true).outcome()).isEqualTo(PartnerAnswer.Outcome.ACCEPTED);
        assertThat(seen).extracting(Seen::method).containsExactly("GET", "POST");

        seen.clear();
        replies.clear();
        replies.add(notFound());
        OutboundTransfer withoutSender = new OutboundTransfer(MESSAGE, bankB, "COOBS", null, "1000-2000-3000",
                "Ana Lim", new BigDecimal("2500.00"), "PHP", null, "TX-1", Instant.now());
        assertThat(client.deliver(withoutSender, true).outcome()).isEqualTo(PartnerAnswer.Outcome.UNKNOWN);
        assertThat(seen).extracting(Seen::method).containsExactly("GET");
    }

    @Test
    @DisplayName("a partner with no address is our own loopback: it accepts, except an account beginning 0000")
    void loopbackPartner() {
        bankB.setBaseUrl(null);

        PartnerAnswer accepted = client.deliver(transfer(), false);
        assertThat(accepted.outcome()).isEqualTo(PartnerAnswer.Outcome.ACCEPTED);
        assertThat(accepted.reference()).isEqualTo("LB-6F1C0E0A");
        // Asked again, it says the same, as a real partner does for a repeated message ID.
        assertThat(client.deliver(transfer(), true).reference()).isEqualTo("LB-6F1C0E0A");

        OutboundTransfer toNobody = new OutboundTransfer(MESSAGE, bankB, "COOBS", "ACC_10000001", "0000-1111-2222",
                null, new BigDecimal("100.00"), "PHP", null, "TX-2", Instant.now());
        PartnerAnswer refused = client.deliver(toNobody, false);
        assertThat(refused.outcome()).isEqualTo(PartnerAnswer.Outcome.REJECTED);
        assertThat(refused.reasonCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(seen).as("the loopback makes no network call").isEmpty();
    }
}
