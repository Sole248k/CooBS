package com.group2.fse.orchestration_service.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.group2.fse.orchestration_service.dto.SagaDtos.InboundInterbankRequest;
import com.group2.fse.orchestration_service.dto.SagaDtos.StartReversalRequest;
import com.group2.fse.orchestration_service.dto.SagaDtos.StartTransferRequest;
import com.group2.fse.orchestration_service.security.jwt.JwtTokenProvider;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One test per row of the orchestration scenario table that this service can decide on its
 * own. The downstream services are a stub HTTP server answering with the contract examples,
 * so timeouts, status codes and idempotency keys go over a real socket.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransferSagaTest {

    private static final Path EXAMPLES = Path.of("..", "..", "contracts", "examples");
    private static final String RISK = "/internal/v1/risk/evaluate";
    private static final String HOLD = "/internal/v1/ledger/holds";
    private static final String CAPTURE = "/internal/v1/ledger/holds/9001/capture";
    private static final String RELEASE = "/internal/v1/ledger/holds/9001/release";
    private static final String POSTINGS = "/internal/v1/ledger/postings";
    private static final String REVERSAL = "/internal/v1/ledger/reversals";

    private record Stub(int status, String body, long delayMs) {
    }

    private record Call(String path, String idempotencyKey, String authorization, String correlationId) {
    }

    private static final Map<String, Stub> STUBS = new ConcurrentHashMap<>();
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final HttpServer SERVER = startServer();

    @Autowired
    private TransferSaga saga;
    @Autowired
    private ReversalSaga reversalSaga;
    @Autowired
    private InboundInterbankSaga inboundSaga;
    @Autowired
    private SagaRepository sagas;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokens;
    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean
    private AsyncTaskExecutor applicationTaskExecutor;

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + SERVER.getAddress().getPort();
        registry.add("app.clients.customer.base-url", () -> url);
        registry.add("app.clients.risk.base-url", () -> url);
        registry.add("app.clients.ledger.base-url", () -> url);
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @BeforeEach
    void everythingPasses() {
        reset(kafkaTemplate);
        CALLS.clear();
        STUBS.clear();
        STUBS.put("/internal/v1/customers/1/risk-profile", ok("C7-risk-profile.response.json"));
        STUBS.put(RISK, ok("R1-risk-evaluate.response.json"));
        STUBS.put(HOLD, new Stub(201, example("L3-place-hold.response.json"), 0));
        STUBS.put(CAPTURE, ok("L4-capture-hold.response.json"));
        STUBS.put(RELEASE, ok("L5-release-hold.response.json"));
        STUBS.put(POSTINGS, new Stub(200, """
                {"posted":1,"failed":0,"totalAmount":"2500.00",
                 "results":[{"referenceNo":"IB-20261003-000045","status":"POSTED","errorCode":null}]}""", 0));
        STUBS.put(REVERSAL, ok("L7-reversal.response.json"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void everythingPasses_holdsCapturesAndCompletesOnTheLaterEvents() {
        StartTransferRequest t = transfer();

        saga.run(t, "corr-1");

        SagaInstance row = row(t);
        assertThat(row.getState()).isEqualTo(SagaState.POSTED);
        assertThat(row.getHoldId()).isEqualTo(9001L);
        assertThat(row.getRiskDecision()).isEqualTo("APPROVE");
        assertThat(ledgerCalls()).containsExactly(
                new Call(HOLD, t.transactionId() + ":hold", null, null),
                new Call(CAPTURE, t.transactionId() + ":capture", null, null));
        assertThat(CALLS).allSatisfy(call -> {
            assertThat(call.authorization()).startsWith("Bearer ");
            assertThat(tokens.getRoles(call.authorization().substring(7))).containsExactly("ROLE_SERVICE");
            assertThat(call.correlationId()).isEqualTo("corr-1");
        });
        assertThat(stages()).containsExactly("4 FRAUD_CHECK PASSED PROCESSING", "5 LIMIT_CHECK PASSED PROCESSING",
                "6 FUNDS_CHECK PASSED PROCESSING", "7 AUTHORIZED PASSED PROCESSING", "8 POSTED PASSED POSTED");

        saga.onLedgerTransferCompleted(t.transactionId(), t.referenceNo());
        assertThat(row(t).getState()).isEqualTo(SagaState.POSTED);
        saga.onNotificationDispatched(t.transactionId(), t.referenceNo());

        assertThat(row(t).getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(stages()).endsWith("9 LEDGER_UPDATE PASSED POSTED", "10 NOTIFICATION PASSED POSTED",
                "11 RECONCILIATION PASSED POSTED");
    }

    @Test
    void stageEventFollowsTheContractExample() {
        StartTransferRequest t = transfer();

        saga.run(t, "corr-2");

        JsonNode event = events().get(0);
        JsonNode example = jsonMapper.readTree(example("event-transaction-stage-changed.payload.json"));
        assertThat(event.propertyNames()).containsExactlyInAnyOrderElementsOf(
                jsonMapper.readTree(example("event-envelope.json")).propertyNames());
        assertThat(event.path("payload").propertyNames())
                .containsExactlyInAnyOrderElementsOf(example.propertyNames());
        assertThat(event.path("eventType").asString()).isEqualTo("TRANSACTION_STAGE_CHANGED");
        assertThat(event.path("correlationId").asString()).isEqualTo("corr-2");
        assertThat(event.path("payload").path("referenceNo").asString()).isEqualTo(t.referenceNo());
        assertThat(event.path("payload").path("detail").asString()).isEqualTo("score 0.18");
        assertThat(event.path("payload").path("occurredAt").asString()).endsWith("Z");
    }

    @Test
    void riskDeclines_stopsBeforeAnyHold() {
        STUBS.put(RISK, new Stub(200, """
                {"decision":"DECLINE","score":"0.91",
                 "fraudCheck":{"outcome":"FAILED","reasons":["HIGH_RISK"]},
                 "limitCheck":{"outcome":"PASSED"}}""", 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertFailed(t, "RISK_DECLINED");
        assertThat(row(t).getRiskDecision()).isEqualTo("DECLINE");
        assertThat(ledgerCalls()).isEmpty();
        assertThat(stages()).containsExactly("4 FRAUD_CHECK FAILED FAILED");
    }

    @Test
    void overTheDailyLimit_stopsBeforeAnyHold() {
        STUBS.put(RISK, new Stub(200, """
                {"decision":"DECLINE","score":"0.18",
                 "fraudCheck":{"outcome":"PASSED","reasons":[]},
                 "limitCheck":{"outcome":"FAILED"}}""", 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertFailed(t, "LIMIT_EXCEEDED");
        assertThat(ledgerCalls()).isEmpty();
        assertThat(stages()).containsExactly("4 FRAUD_CHECK PASSED PROCESSING", "5 LIMIT_CHECK FAILED FAILED");
    }

    @Test
    void riskEngineSlowerThan150ms_failsClosedWithNoLedgerCall() {
        STUBS.put(RISK, new Stub(200, example("R1-risk-evaluate.response.json"), 600));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertFailed(t, "RISK_UNAVAILABLE");
        assertThat(ledgerCalls()).isEmpty();
        assertThat(stages()).containsExactly("4 FRAUD_CHECK FAILED FAILED");
    }

    @Test
    void riskEngineError_failsClosedWithNoLedgerCall() {
        STUBS.put(RISK, new Stub(500, "{}", 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertFailed(t, "RISK_UNAVAILABLE");
        assertThat(ledgerCalls()).isEmpty();
    }

    @Test
    void notEnoughMoney_holdIsRefusedAtStage6() {
        STUBS.put(HOLD, new Stub(422, example("common-error.json"), 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertFailed(t, "INSUFFICIENT_FUNDS");
        assertThat(ledgerCalls()).extracting(Call::path).containsExactly(HOLD);
        assertThat(stages()).endsWith("6 FUNDS_CHECK FAILED FAILED");
    }

    @Test
    void ledgerUnreachableBeforeTheHold_failsWithNothingToUndo() {
        STUBS.put(HOLD, new Stub(503, "{}", 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertFailed(t, "LEDGER_UNAVAILABLE");
        assertThat(ledgerCalls()).extracting(Call::path).containsExactly(HOLD);
    }

    @Test
    void captureRefused_releasesTheHold() {
        STUBS.put(CAPTURE, new Stub(422, """
                {"status":422,"errorCode":"ACCOUNT_NOT_ACTIVE"}""", 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        SagaInstance row = row(t);
        assertThat(row.getState()).isEqualTo(SagaState.COMPENSATED);
        assertThat(row.getFailureCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        assertThat(ledgerCalls()).containsExactly(
                new Call(HOLD, t.transactionId() + ":hold", null, null),
                new Call(CAPTURE, t.transactionId() + ":capture", null, null),
                new Call(RELEASE, t.transactionId() + ":release", null, null));
        assertThat(stages()).endsWith("8 POSTED FAILED FAILED");
    }

    @Test
    void ledgerUnreachableAfterTheHold_retriesWithTheSameKeyAndNeverReleases() {
        STUBS.put(CAPTURE, new Stub(503, "{}", 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertThat(row(t).getState()).isEqualTo(SagaState.FUNDS_RESERVED);
        assertThat(row(t).getFailureCode()).isNull();
        Call capture = new Call(CAPTURE, t.transactionId() + ":capture", null, null);
        assertThat(ledgerCalls()).containsExactly(
                new Call(HOLD, t.transactionId() + ":hold", null, null), capture, capture, capture);
        assertThat(stages()).endsWith("7 AUTHORIZED PASSED PROCESSING");
    }

    @Test
    void restartMidSaga_continuesFromTheSavedState() {
        StartTransferRequest t = transfer();
        STUBS.put(CAPTURE, new Stub(503, "{}", 0));
        saga.run(t, "corr");
        assertThat(row(t).getState()).isEqualTo(SagaState.FUNDS_RESERVED);
        CALLS.clear();
        STUBS.put(CAPTURE, ok("L4-capture-hold.response.json"));

        saga.run(t, "corr");

        assertThat(row(t).getState()).isEqualTo(SagaState.POSTED);
        assertThat(CALLS).extracting(Call::path).containsExactly(CAPTURE);
    }

    @Test
    void finishedSaga_isNotRunAgain() {
        StartTransferRequest t = transfer();
        saga.run(t, "corr");
        CALLS.clear();

        saga.run(t, "corr");

        assertThat(CALLS).isEmpty();
        assertThat(row(t).getState()).isEqualTo(SagaState.POSTED);
    }

    @Test
    void inboundInterbank_postsOnceAndPublishesAllElevenStages() {
        InboundInterbankRequest r = jsonMapper.readValue(example("O4-inbound-interbank-saga.request.json"),
                InboundInterbankRequest.class);

        SagaInstance row = inboundSaga.run(r, "corr");
        inboundSaga.run(r, "corr");

        assertThat(row.getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(CALLS).extracting(Call::path, Call::idempotencyKey)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(POSTINGS, r.transactionId() + ":posting"));
        assertThat(stages()).hasSize(11).contains("3 AUTHENTICATED PASSED PROCESSING", "11 RECONCILIATION PASSED POSTED");
        assertThat(events().get(2).path("payload").path("detail").asString()).isEqualTo("not applicable");
    }

    @Test
    void internalEndpoints_acceptOnlyAServiceToken() throws Exception {
        String body = example("O1-start-transfer-saga.request.json");
        String service = tokens.generateToken(0L, "transaction-service", List.of("ROLE_SERVICE"));
        String customer = tokens.generateToken(1L, "john_doe", List.of("ROLE_CUSTOMER"));

        mockMvc.perform(post("/internal/v1/sagas/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/v1/sagas/transfers").header("Authorization", "Bearer " + customer)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/v1/sagas/transfers").header("Authorization", "Bearer " + service)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11"))
                .andExpect(jsonPath("$.sagaState").value("STARTED"));
        mockMvc.perform(get("/internal/v1/sagas/6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11")
                .header("Authorization", "Bearer " + service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sagaState").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
        mockMvc.perform(get("/internal/v1/sagas/" + UUID.randomUUID()).header("Authorization", "Bearer " + service))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSACTION_NOT_FOUND"));
        mockMvc.perform(post("/internal/v1/sagas/transfers").header("Authorization", "Bearer " + service)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_PARAMETERS"));
    }

    @Test
    void reversalOfPostedTransfer_callsLedgerReverseAndCompletes() {
        UUID origId = UUID.randomUUID();
        SagaInstance original = sagas.findOrCreate(origId);
        original.setReferenceNo("TX-20261003-8F3A21C0");
        original.setState(SagaState.COMPLETED);
        sagas.saveAndFlush(original);

        UUID revId = UUID.randomUUID();
        StartReversalRequest revReq = new StartReversalRequest(
                revId, origId, "RV-20261003-11AA22BB", "Customer dispute approved", 3L);

        reversalSaga.run(revReq, "corr-rev-1");

        SagaInstance revSaved = sagas.findByTransactionId(revId).orElseThrow();
        assertThat(revSaved.getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(revSaved.getReferenceNo()).isEqualTo("RV-20261003-11AA22BB");

        List<Call> calls = ledgerCalls();
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).path()).isEqualTo("/internal/v1/ledger/reversals");
        assertThat(calls.get(0).idempotencyKey()).isEqualTo(revId + ":reversal");
    }

    @Test
    void reversalOfHeldTransfer_releasesHoldAndCompensatesOriginal() {
        UUID origId = UUID.randomUUID();
        SagaInstance original = sagas.findOrCreate(origId);
        original.setReferenceNo("TX-HELD-001");
        original.setHoldId(9001L);
        original.setState(SagaState.FUNDS_RESERVED);
        sagas.saveAndFlush(original);

        UUID revId = UUID.randomUUID();
        StartReversalRequest revReq = new StartReversalRequest(
                revId, origId, "RV-CANCEL-001", "Interbank drop cancellation", 3L);

        reversalSaga.run(revReq, "corr-rev-2");

        SagaInstance origSaved = sagas.findByTransactionId(origId).orElseThrow();
        assertThat(origSaved.getState()).isEqualTo(SagaState.COMPENSATED);

        SagaInstance revSaved = sagas.findByTransactionId(revId).orElseThrow();
        assertThat(revSaved.getState()).isEqualTo(SagaState.COMPLETED);

        List<Call> calls = ledgerCalls();
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).path()).isEqualTo("/internal/v1/ledger/holds/9001/release");
        assertThat(calls.get(0).idempotencyKey()).isEqualTo(origId + ":release");
    }

    @Test
    void reversalOfUnknownTransfer_failsWithTransactionNotFound() {
        UUID revId = UUID.randomUUID();
        StartReversalRequest revReq = new StartReversalRequest(
                revId, UUID.randomUUID(), "RV-MISSING-001", "Dispute unknown", 3L);

        reversalSaga.run(revReq, "corr-rev-3");

        SagaInstance revSaved = sagas.findByTransactionId(revId).orElseThrow();
        assertThat(revSaved.getState()).isEqualTo(SagaState.FAILED);
        assertThat(revSaved.getFailureCode()).isEqualTo("TRANSACTION_NOT_FOUND");
    }

    @Test
    void reversalEndpoint_withServiceToken_acceptsO2() throws Exception {
        String service = tokens.generateToken(0L, "transaction-service", List.of("ROLE_SERVICE"));
        String body = example("O2-start-reversal-saga.request.json");

        mockMvc.perform(post("/internal/v1/sagas/reversals").header("Authorization", "Bearer " + service)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value("c4b7e2d9-5a13-4f86-b0c2-7d9e1a3f5b64"))
                .andExpect(jsonPath("$.sagaState").value("STARTED"));

        mockMvc.perform(post("/internal/v1/sagas/reversals")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    /** The O1 contract example with a fresh transaction ID. */
    private StartTransferRequest transfer() {
        StartTransferRequest e = jsonMapper.readValue(example("O1-start-transfer-saga.request.json"),
                StartTransferRequest.class);
        return new StartTransferRequest(UUID.randomUUID(), e.referenceNo(), e.channel(), e.customerId(),
                e.sourceAccountId(), e.destinationAccountId(), e.destinationBankCode(), e.amount(), e.currency(),
                e.actorId(), e.actorType());
    }

    private SagaInstance row(StartTransferRequest t) {
        return sagas.findByTransactionId(t.transactionId()).orElseThrow();
    }

    private void assertFailed(StartTransferRequest t, String failureCode) {
        assertThat(row(t).getState()).isEqualTo(SagaState.FAILED);
        assertThat(row(t).getFailureCode()).isEqualTo(failureCode);
        assertThat(row(t).getHoldId()).isNull();
    }

    /** Ledger calls in order, reduced to path and idempotency key. */
    private List<Call> ledgerCalls() {
        return CALLS.stream().filter(call -> call.path().contains("/ledger/"))
                .map(call -> new Call(call.path(), call.idempotencyKey(), null, null)).toList();
    }

    private List<JsonNode> events() {
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, atLeast(0)).send(anyString(), anyString(), value.capture());
        return value.getAllValues().stream().map(jsonMapper::readTree).toList();
    }

    /** Published stage events as "stageNo stageName outcome transactionStatus". */
    private List<String> stages() {
        return events().stream().map(event -> event.path("payload")).map(p -> p.path("stageNo").asInt() + " "
                + p.path("stageName").asString() + " " + p.path("outcome").asString() + " "
                + p.path("transactionStatus").asString()).toList();
    }

    private static Stub ok(String exampleFile) {
        return new Stub(200, example(exampleFile), 0);
    }

    private static String example(String file) {
        try {
            return Files.readString(EXAMPLES.resolve(file));
        } catch (IOException e) {
            throw new IllegalStateException("Contract example not found: " + file, e);
        }
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                exchange.getRequestBody().readAllBytes();
                CALLS.add(new Call(path, exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("X-Correlation-ID")));
                Stub stub = STUBS.getOrDefault(path, new Stub(404, "{}", 0));
                try {
                    Thread.sleep(stub.delayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] body = stub.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(stub.status(), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
