package com.group2.fse.orchestration_service.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.group2.fse.orchestration_service.client.DownstreamException;
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
// The scheduled sweep is pushed out of reach; the tests call sweep() themselves.
@SpringBootTest(properties = "app.saga.sweep-interval=1d")
@AutoConfigureMockMvc
class TransferSagaTest {

    private static final Path EXAMPLES = Path.of("..", "..", "contracts", "examples");
    private static final String RISK = "/internal/v1/risk/evaluate";
    private static final String OUTCOMES = "/internal/v1/risk/outcomes";
    private static final String HOLD = "/internal/v1/ledger/holds";
    private static final String CAPTURE = "/internal/v1/ledger/holds/9001/capture";
    private static final String RELEASE = "/internal/v1/ledger/holds/9001/release";
    private static final String POSTINGS = "/internal/v1/ledger/postings";
    private static final String REVERSAL = "/internal/v1/ledger/reversals";
    private static final String INBOUND = "/internal/v1/sagas/inbound-interbank";

    private record Stub(int status, String body, long delayMs) {
    }

    private record Call(String path, String idempotencyKey, String authorization, String correlationId) {
    }

    /** L8 answering that the one posting of an inbound credit is in the journal. */
    private static final Stub POSTED_ONCE = new Stub(200, """
            {"posted":1,"failed":0,"totalAmount":"2500.00",
             "results":[{"referenceNo":"IB-20261003-000045","status":"POSTED","errorCode":null}]}""", 0);

    private static final Map<String, Stub> STUBS = new ConcurrentHashMap<>();
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    /** Run once, while the stub is answering that path: what another thread does in the meantime. */
    private static final Map<String, Runnable> MEANWHILE = new ConcurrentHashMap<>();
    /** Every request body the stub received, as "path body". */
    private static final List<String> BODIES = new CopyOnWriteArrayList<>();
    private static final HttpServer SERVER = startServer();

    @Autowired
    private TransferSaga saga;
    @Autowired
    private ReversalSaga reversalSaga;
    @Autowired
    private InboundInterbankSaga inboundSaga;
    @Autowired
    private SagaRecoveryRunner recovery;
    @Autowired
    private SagaRepository sagas;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbc;
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
        MEANWHILE.clear();
        BODIES.clear();
        STUBS.put("/internal/v1/customers/1/risk-profile", ok("C7-risk-profile.response.json"));
        STUBS.put(RISK, ok("R1-risk-evaluate.response.json"));
        STUBS.put(OUTCOMES, ok("R2-risk-outcome.response.json"));
        STUBS.put(HOLD, new Stub(201, example("L3-place-hold.response.json"), 0));
        STUBS.put(CAPTURE, ok("L4-capture-hold.response.json"));
        STUBS.put(RELEASE, ok("L5-release-hold.response.json"));
        STUBS.put(POSTINGS, POSTED_ONCE);
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
    void newBeneficiary_isPassedOnToTheRiskEngine() {
        StartTransferRequest t = transferWithNewBeneficiary(true);

        saga.run(t, "corr");

        assertThat(riskRequest(t).path("isNewBeneficiary").asBoolean()).isTrue();
    }

    @Test
    void knownBeneficiaryOrAStartWithoutTheFlag_isEvaluatedAsNotNew() {
        StartTransferRequest known = transferWithNewBeneficiary(false);
        saga.run(known, "corr");
        assertThat(riskRequest(known).path("isNewBeneficiary").isBoolean()).isTrue();
        assertThat(riskRequest(known).path("isNewBeneficiary").asBoolean()).isFalse();

        // A start sent by a caller that does not know the field yet.
        StartTransferRequest withoutTheFlag = transferWithNewBeneficiary(null);
        saga.run(withoutTheFlag, "corr");
        assertThat(riskRequest(withoutTheFlag).path("isNewBeneficiary").isBoolean()).isTrue();
        assertThat(riskRequest(withoutTheFlag).path("isNewBeneficiary").asBoolean()).isFalse();
    }

    @Test
    void newBeneficiary_isKeptInTheSavedPayloadSoARecoveredSagaAsksTheSameQuestion() throws Exception {
        StartTransferRequest t = transferWithNewBeneficiary(true);
        String service = tokens.generateToken(0L, "transaction-service", List.of("ROLE_SERVICE"));
        // The mocked executor drops the task: the service went down between the 202 and the run.
        mockMvc.perform(post("/internal/v1/sagas/transfers").header("Authorization", "Bearer " + service)
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(t)))
                .andExpect(status().isAccepted());
        assertThat(row(t).getPayload()).contains("\"isNewBeneficiary\":true").doesNotContain("\"newBeneficiary\"");
        runTasksInline();

        recovery.run(null);

        assertThat(row(t).getState()).isEqualTo(SagaState.POSTED);
        assertThat(riskRequest(t).path("isNewBeneficiary").asBoolean()).isTrue();
    }

    // --- the daily limit is a reservation: an approved transfer that does not post gives it back ---

    @Test
    void notEnoughMoney_givesTheDailyLimitReservationBack() {
        STUBS.put(HOLD, new Stub(422, example("common-error.json"), 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertFailed(t, "INSUFFICIENT_FUNDS");
        assertThat(limitReleases(t.transactionId())).isEqualTo(1);
    }

    @Test
    void captureRefused_releasesTheHoldAndThenTheLimit() {
        STUBS.put(CAPTURE, new Stub(422, example("common-error.json"), 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertThat(row(t).getState()).isEqualTo(SagaState.COMPENSATED);
        assertThat(limitReleases(t.transactionId())).isEqualTo(1);
        // The money is made whole first; the limit follows.
        List<String> paths = CALLS.stream().map(Call::path).toList();
        assertThat(paths.indexOf(RELEASE)).isLessThan(paths.indexOf(OUTCOMES));
    }

    @Test
    void postedTransferAndDeclinedTransfer_reportNoOutcome() {
        StartTransferRequest posted = transfer();
        saga.run(posted, "corr");
        assertThat(row(posted).getState()).isEqualTo(SagaState.POSTED);

        // Declined by risk: nothing was reserved, so there is nothing to give back.
        STUBS.put(RISK, new Stub(200, """
                {"decision":"DECLINE","score":"0.91",
                 "fraudCheck":{"outcome":"FAILED","reasons":["HIGH_RISK"]},
                 "limitCheck":{"outcome":"PASSED"}}""", 0));
        StartTransferRequest declined = transfer();
        saga.run(declined, "corr");

        assertThat(CALLS).extracting(Call::path).doesNotContain(OUTCOMES);
    }

    @Test
    void limitReleaseThatGetsNoAnswer_doesNotChangeHowTheTransferEnded() {
        STUBS.put(HOLD, new Stub(422, example("common-error.json"), 0));
        STUBS.put(OUTCOMES, new Stub(503, "{}", 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertFailed(t, "INSUFFICIENT_FUNDS");
        assertThat(stages()).endsWith("6 FUNDS_CHECK FAILED FAILED");
    }

    @Test
    void holdGivenUpAfterTheRetryWindow_givesTheLimitBackOnceTheHoldIsReleased() {
        StartTransferRequest t = riskApproved(Duration.ofMinutes(10));

        saga.run(t, "corr");

        assertThat(row(t).getState()).isEqualTo(SagaState.FAILED);
        assertThat(row(t).getFailureCode()).isEqualTo("LEDGER_UNAVAILABLE");
        assertThat(limitReleases(t.transactionId())).isEqualTo(1);
    }

    @Test
    void reversalOfATransferThatWasHeldOrOnlyApproved_givesItsLimitBack() {
        UUID heldId = UUID.randomUUID();
        SagaInstance held = sagas.findOrCreate(heldId);
        held.setReferenceNo("TX-HELD-LIMIT-001");
        held.setHoldId(9001L);
        held.setState(SagaState.FUNDS_RESERVED);
        sagas.saveAndFlush(held);
        UUID approvedId = UUID.randomUUID();
        SagaInstance approved = sagas.findOrCreate(approvedId);
        approved.setReferenceNo("TX-APPROVED-LIMIT-001");
        approved.setState(SagaState.RISK_APPROVED);
        sagas.saveAndFlush(approved);

        reversalSaga.run(new StartReversalRequest(UUID.randomUUID(), heldId, "RV-LIMIT-001", "Sent by mistake", 3L), "corr");
        reversalSaga.run(new StartReversalRequest(UUID.randomUUID(), approvedId, "RV-LIMIT-002", "Sent by mistake", 3L), "corr");

        assertThat(limitReleases(heldId)).isEqualTo(1);
        assertThat(limitReleases(approvedId)).isEqualTo(1);
    }

    @Test
    void reversalOfAPostedTransfer_isCountedForItsSenderAndDoesNotGiveTheLimitBack() {
        StartTransferRequest t = transfer();
        saga.run(t, "corr");
        assertThat(row(t).getState()).isEqualTo(SagaState.POSTED);

        reversalSaga.run(new StartReversalRequest(UUID.randomUUID(), t.transactionId(), "RV-LIMIT-003", "Dispute", 3L),
                "corr");

        // One report, REVERSED, naming the sender. A reversal corrects a posting; it is not a
        // refund of the day's allowance, so nothing reports FAILED.
        List<JsonNode> reports = outcomes(t.transactionId());
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).path("outcome").asString()).isEqualTo("REVERSED");
        assertThat(reports.get(0).path("customerId").asLong()).isEqualTo(t.customerId());
        // The ledger has reversed before the engine is told.
        List<String> paths = CALLS.stream().map(Call::path).toList();
        assertThat(paths.indexOf(REVERSAL)).isLessThan(paths.lastIndexOf(OUTCOMES));
    }

    @Test
    void reversalOfATransferWithNoKnownSender_reportsNothing() {
        // A seeded row has no payload, so there is no customer of ours to count the reversal for.
        UUID origId = UUID.randomUUID();
        SagaInstance original = sagas.findOrCreate(origId);
        original.setReferenceNo("TX-20261003-8F3A21C0");
        original.setState(SagaState.POSTED);
        sagas.saveAndFlush(original);
        UUID reversalId = UUID.randomUUID();

        reversalSaga.run(new StartReversalRequest(reversalId, origId, "RV-LIMIT-004", "Dispute", 3L), "corr");

        assertThat(sagas.findByTransactionId(reversalId).orElseThrow().getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(CALLS).extracting(Call::path).doesNotContain(OUTCOMES);
    }

    @Test
    void reversalRefusedOrUnanswered_isNotCounted() {
        StartTransferRequest t = transfer();
        saga.run(t, "corr");
        CALLS.clear();
        BODIES.clear();

        STUBS.put(REVERSAL, new Stub(503, "{}", 0));
        reversalSaga.run(new StartReversalRequest(UUID.randomUUID(), t.transactionId(), "RV-LIMIT-005", "Dispute", 3L),
                "corr");
        STUBS.put(REVERSAL, new Stub(409, example("common-error.json"), 0));
        reversalSaga.run(new StartReversalRequest(UUID.randomUUID(), t.transactionId(), "RV-LIMIT-006", "Dispute", 3L),
                "corr");

        assertThat(outcomes(t.transactionId())).isEmpty();
    }

    @Test
    void reversalCountThatGetsNoAnswer_doesNotChangeHowTheReversalEnded() {
        StartTransferRequest t = transfer();
        saga.run(t, "corr");
        STUBS.put(OUTCOMES, new Stub(503, "{}", 0));
        UUID reversalId = UUID.randomUUID();

        reversalSaga.run(new StartReversalRequest(reversalId, t.transactionId(), "RV-LIMIT-007", "Dispute", 3L), "corr");

        assertThat(sagas.findByTransactionId(reversalId).orElseThrow().getState()).isEqualTo(SagaState.COMPLETED);
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
    void riskEngineSlowerThanItsBudget_failsClosedWithNoLedgerCall() {
        // The budget is 150 ms in production and 1 s in the test profile; the stub is twice as slow.
        STUBS.put(RISK, new Stub(200, example("R1-risk-evaluate.response.json"), 2000));
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
    void transferForAnotherBank_isRefusedBeforeRiskOrTheLedgerIsAsked() {
        StartTransferRequest t = transferTo("BPI");

        saga.run(t, "corr");

        assertFailed(t, "INTERBANK_REJECTED");
        assertThat(row(t).getRiskDecision()).isNull();
        assertThat(CALLS).isEmpty();
        assertThat(stages()).containsExactly("4 FRAUD_CHECK FAILED FAILED");
        JsonNode payload = events().get(0).path("payload");
        assertThat(payload.path("failureCode").asString()).isEqualTo("INTERBANK_REJECTED");
        assertThat(payload.path("customerMessage").asString()).contains("other banks");
    }

    @Test
    void transferForAnotherBank_staysRefusedWhenTheSagaIsRunAgain() {
        StartTransferRequest t = transferTo("BPI");
        saga.run(t, "corr");

        saga.run(t, "corr");

        assertFailed(t, "INTERBANK_REJECTED");
        assertThat(CALLS).isEmpty();
        assertThat(stages()).containsExactly("4 FRAUD_CHECK FAILED FAILED");
    }

    @Test
    void missingBankCodeOrOurOwnInAnyCase_isAnInternalTransfer() {
        for (String bankCode : new String[] {null, " ", "coobs", " COOBS "}) {
            StartTransferRequest t = transferTo(bankCode);

            saga.run(t, "corr");

            assertThat(row(t).getState()).as("bank code '%s'", bankCode).isEqualTo(SagaState.POSTED);
        }
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
    void ledgerErrorOnTheHold_isNotMarkedFailedBecauseTheHoldMayExist() {
        STUBS.put(HOLD, new Stub(503, "{}", 0));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertThat(row(t).getState()).isEqualTo(SagaState.RISK_APPROVED);
        assertThat(row(t).getFailureCode()).isNull();
        assertThat(ledgerCalls()).extracting(Call::path).containsExactly(HOLD);
        assertThat(stages()).endsWith("5 LIMIT_CHECK PASSED PROCESSING");
    }

    @Test
    void holdTimesOut_isNotMarkedFailedAndTheNextRunRepeatsTheSameHold() {
        // Slower than the 2 s ledger client timeout: the ledger may well have placed the hold.
        STUBS.put(HOLD, new Stub(201, example("L3-place-hold.response.json"), 2500));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertThat(row(t).getState()).isEqualTo(SagaState.RISK_APPROVED);
        assertThat(row(t).getFailureCode()).isNull();
        assertThat(stages()).noneMatch(stage -> stage.contains("FAILED"));

        STUBS.put(HOLD, new Stub(201, example("L3-place-hold.response.json"), 0));
        saga.run(t, "corr");

        assertThat(row(t).getState()).isEqualTo(SagaState.POSTED);
        Call hold = new Call(HOLD, t.transactionId() + ":hold", null, null);
        assertThat(ledgerCalls()).containsExactly(hold, hold,
                new Call(CAPTURE, t.transactionId() + ":capture", null, null));
    }

    @Test
    void holdStillUnansweredAfterTheRetryWindow_releasesTheHoldTheLedgerReturnsAndFails() {
        StartTransferRequest t = riskApproved(Duration.ofMinutes(10));

        saga.run(t, "corr");

        SagaInstance row = row(t);
        assertThat(row.getState()).isEqualTo(SagaState.FAILED);
        assertThat(row.getFailureCode()).isEqualTo("LEDGER_UNAVAILABLE");
        assertThat(row.getHoldId()).isEqualTo(9001L);
        assertThat(ledgerCalls()).containsExactly(
                new Call(HOLD, t.transactionId() + ":hold", null, null),
                new Call(RELEASE, t.transactionId() + ":release", null, null));
        assertThat(stages()).containsExactly("6 FUNDS_CHECK FAILED FAILED");
    }

    @Test
    void holdStillUnansweredAfterTheRetryWindow_staysInFlightWhileTheLedgerIsSilent() {
        STUBS.put(HOLD, new Stub(503, "{}", 0));
        StartTransferRequest t = riskApproved(Duration.ofMinutes(10));

        saga.run(t, "corr");

        assertThat(row(t).getState()).isEqualTo(SagaState.RISK_APPROVED);
        assertThat(row(t).getFailureCode()).isNull();
        assertThat(ledgerCalls()).extracting(Call::path).containsExactly(HOLD);
    }

    @Test
    void holdPlacedAfterTheRetryWindow_releaseUnanswered_staysCompensatingAndTheNextRunReleases() {
        STUBS.put(RELEASE, new Stub(503, "{}", 0));
        StartTransferRequest t = riskApproved(Duration.ofMinutes(10));
        saga.run(t, "corr");
        assertThat(row(t).getState()).isEqualTo(SagaState.COMPENSATING);
        assertThat(row(t).getHoldId()).isEqualTo(9001L);
        CALLS.clear();
        STUBS.put(RELEASE, ok("L5-release-hold.response.json"));

        saga.run(t, "corr");

        assertThat(row(t).getState()).isEqualTo(SagaState.FAILED);
        assertThat(row(t).getFailureCode()).isEqualTo("LEDGER_UNAVAILABLE");
        // The hold is released first; only then is the day's limit given back.
        assertThat(CALLS).extracting(Call::path).containsExactly(RELEASE, OUTCOMES);
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
    void startupRecovery_capturesTheHoldLeftAtFundsReservedAndNeverReleasesIt() {
        StartTransferRequest t = transfer();
        STUBS.put(CAPTURE, new Stub(503, "{}", 0));
        saga.run(t, "corr");
        assertThat(row(t).getState()).isEqualTo(SagaState.FUNDS_RESERVED);
        sagas.saveAndFlush(new SagaInstance(UUID.randomUUID())); // no payload: skipped, not fatal
        CALLS.clear();
        STUBS.put(CAPTURE, ok("L4-capture-hold.response.json"));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(applicationTaskExecutor).execute(any(Runnable.class));

        recovery.run(null);

        assertThat(row(t).getState()).isEqualTo(SagaState.POSTED);
        assertThat(ledgerCalls()).contains(new Call(CAPTURE, t.transactionId() + ":capture", null, null));
        assertThat(CALLS).extracting(Call::path).doesNotContain(RELEASE, HOLD, RISK);
    }

    @Test
    void scheduledSweep_resumesAStaleRiskApprovedSagaAndLeavesAFreshOneAlone() {
        StartTransferRequest stale = transfer();
        StartTransferRequest fresh = transfer();
        for (StartTransferRequest t : List.of(stale, fresh)) {
            SagaInstance row = sagas.findOrCreate(t.transactionId(), t.referenceNo(),
                    jsonMapper.writeValueAsString(t));
            row.setState(SagaState.RISK_APPROVED);
            sagas.saveAndFlush(row);
        }
        lastWritten(stale, Duration.ofMinutes(2));
        runTasksInline();

        recovery.sweep();

        assertThat(row(stale).getState()).isEqualTo(SagaState.POSTED);
        assertThat(row(fresh).getState()).isEqualTo(SagaState.RISK_APPROVED);
        assertThat(ledgerCalls()).contains(new Call(HOLD, stale.transactionId() + ":hold", null, null),
                new Call(CAPTURE, stale.transactionId() + ":capture", null, null));
        assertThat(ledgerCalls()).extracting(Call::idempotencyKey).noneMatch(
                key -> key.startsWith(fresh.transactionId().toString()));
    }

    @Test
    void transferAcceptedButNeverRun_isRecoveredFromThePayloadSavedWithTheRow() throws Exception {
        StartTransferRequest t = transfer();
        String service = tokens.generateToken(0L, "transaction-service", List.of("ROLE_SERVICE"));
        // The mocked executor drops the task: the service went down between the 202 and the run.
        mockMvc.perform(post("/internal/v1/sagas/transfers").header("Authorization", "Bearer " + service)
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(t)))
                .andExpect(status().isAccepted());
        assertThat(row(t).getState()).isEqualTo(SagaState.STARTED);
        assertThat(CALLS).isEmpty();
        runTasksInline();

        recovery.run(null);

        assertThat(row(t).getState()).isEqualTo(SagaState.POSTED);
        assertThat(row(t).getReferenceNo()).isEqualTo(t.referenceNo());
        assertThat(ledgerCalls()).contains(new Call(HOLD, t.transactionId() + ":hold", null, null),
                new Call(CAPTURE, t.transactionId() + ":capture", null, null));
    }

    @Test
    void reversalAcceptedButNeverRun_isRecoveredFromThePayloadSavedWithTheRow() throws Exception {
        StartReversalRequest r = new StartReversalRequest(
                UUID.randomUUID(), UUID.randomUUID(), "RV-NEVER-RAN-001", "Dispute unknown", 3L);
        String service = tokens.generateToken(0L, "transaction-service", List.of("ROLE_SERVICE"));
        mockMvc.perform(post("/internal/v1/sagas/reversals").header("Authorization", "Bearer " + service)
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(r)))
                .andExpect(status().isAccepted());
        runTasksInline();

        recovery.run(null);

        SagaInstance row = sagas.findByTransactionId(r.transactionId()).orElseThrow();
        assertThat(row.getState()).isEqualTo(SagaState.FAILED);
        assertThat(row.getFailureCode()).isEqualTo("TRANSACTION_NOT_FOUND");
    }

    @Test
    void kafkaRejectsEveryStageEvent_sagaStillPosts() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("broker unavailable"));
        StartTransferRequest t = transfer();

        saga.run(t, "corr");

        assertThat(row(t).getState()).isEqualTo(SagaState.POSTED);
        assertThat(ledgerCalls()).containsExactly(
                new Call(HOLD, t.transactionId() + ":hold", null, null),
                new Call(CAPTURE, t.transactionId() + ":capture", null, null));
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
    void reversalCancelsWhileTheHoldIsBeingPlaced_theTransferDoesNotOverwriteItAndNeverPosts() {
        StartTransferRequest t = transfer();
        StartReversalRequest r = new StartReversalRequest(
                UUID.randomUUID(), t.transactionId(), "RV-RACE-001", "Customer cancelled", 3L);
        // The reversal finds the transfer at RISK_APPROVED and cancels it while the transfer's
        // own thread is still waiting for the ledger to answer the hold.
        MEANWHILE.put(HOLD, () -> reversalSaga.run(r, "corr-rev"));

        saga.run(t, "corr");

        assertThat(sagas.findByTransactionId(r.transactionId()).orElseThrow().getState())
                .isEqualTo(SagaState.COMPLETED);
        SagaInstance row = row(t);
        assertThat(row.getState()).isEqualTo(SagaState.FAILED);
        assertThat(row.getFailureCode()).isEqualTo("CANCELLED_BEFORE_HOLD");
        // The hold the ledger placed for the cancelled transfer is given back, never captured.
        assertThat(ledgerCalls()).containsExactly(
                new Call(HOLD, t.transactionId() + ":hold", null, null),
                new Call(RELEASE, t.transactionId() + ":release", null, null));
    }

    @Test
    void sagaRowSavedFromAStaleCopy_isRejected() {
        UUID id = UUID.randomUUID();
        sagas.findOrCreate(id);
        SagaInstance mine = sagas.findByTransactionId(id).orElseThrow();
        SagaInstance theirs = sagas.findByTransactionId(id).orElseThrow();
        theirs.setState(SagaState.FAILED);
        sagas.saveAndFlush(theirs);

        mine.setState(SagaState.RISK_APPROVED);

        assertThatThrownBy(() -> sagas.saveAndFlush(mine)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(sagas.findByTransactionId(id).orElseThrow().getState()).isEqualTo(SagaState.FAILED);
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
    @Test
    void reversalGetsNoAnswerFromTheLedger_staysInFlightAndTheNextRunRepeatsTheSameReversal() {
        UUID origId = UUID.randomUUID();
        SagaInstance original = sagas.findOrCreate(origId);
        original.setReferenceNo("TX-20261003-8F3A21C0");
        original.setState(SagaState.POSTED);
        sagas.saveAndFlush(original);
        StartReversalRequest revReq = new StartReversalRequest(
                UUID.randomUUID(), origId, "RV-20261003-11AA22BB", "Customer dispute approved", 3L);
        STUBS.put(REVERSAL, new Stub(503, "{}", 0));

        reversalSaga.run(revReq, "corr-rev-silent");

        SagaInstance waiting = sagas.findByTransactionId(revReq.transactionId()).orElseThrow();
        assertThat(waiting.getState()).isEqualTo(SagaState.STARTED);
        assertThat(waiting.getFailureCode()).isNull();

        STUBS.put(REVERSAL, ok("L7-reversal.response.json"));
        reversalSaga.run(revReq, "corr-rev-silent");

        assertThat(sagas.findByTransactionId(revReq.transactionId()).orElseThrow().getState())
                .isEqualTo(SagaState.COMPLETED);
        assertThat(ledgerCalls()).containsExactly(
                new Call(REVERSAL, revReq.transactionId() + ":reversal", null, null),
                new Call(REVERSAL, revReq.transactionId() + ":reversal", null, null));
    }

    @Test
    void reversalRefusedByTheLedger_failsWithTheLedgersCode() {
        UUID origId = UUID.randomUUID();
        SagaInstance original = sagas.findOrCreate(origId);
        original.setReferenceNo("TX-20261003-8F3A21C0");
        original.setState(SagaState.POSTED);
        sagas.saveAndFlush(original);
        StartReversalRequest revReq = new StartReversalRequest(
                UUID.randomUUID(), origId, "RV-20261003-22BB33CC", "Second dispute", 3L);
        STUBS.put(REVERSAL, new Stub(409, "{\"errorCode\":\"ALREADY_REVERSED\",\"status\":409}", 0));

        reversalSaga.run(revReq, "corr-rev-refused");

        SagaInstance failed = sagas.findByTransactionId(revReq.transactionId()).orElseThrow();
        assertThat(failed.getState()).isEqualTo(SagaState.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo("ALREADY_REVERSED");
    }

    @Test
    void reversalOfHeldTransfer_releaseUnanswered_staysInFlightAndTheOriginalKeepsItsState() {
        UUID origId = UUID.randomUUID();
        SagaInstance original = sagas.findOrCreate(origId);
        original.setReferenceNo("TX-HELD-002");
        original.setHoldId(9001L);
        original.setState(SagaState.FUNDS_RESERVED);
        sagas.saveAndFlush(original);
        StartReversalRequest revReq = new StartReversalRequest(
                UUID.randomUUID(), origId, "RV-CANCEL-002", "Cancelled at the counter", 3L);
        STUBS.put(RELEASE, new Stub(503, "{}", 0));

        reversalSaga.run(revReq, "corr-rev-release-silent");

        assertThat(sagas.findByTransactionId(revReq.transactionId()).orElseThrow().getState())
                .isEqualTo(SagaState.STARTED);
        assertThat(sagas.findByTransactionId(origId).orElseThrow().getState()).isEqualTo(SagaState.FUNDS_RESERVED);
    }

    // --- inbound inter-bank: the ways the credit does not post ---

    @Test
    void inboundInterbank_postingRefusedInTheAnswer_failsAtStage8WithTheLedgersCode() {
        // L8 answers 200 even when it refuses a posting; the outcome is in the body.
        STUBS.put(POSTINGS, new Stub(200, """
                {"posted":0,"failed":1,"totalAmount":"0.00",
                 "results":[{"referenceNo":"IB-20261003-000045","status":"FAILED","errorCode":"ACCOUNT_NOT_ACTIVE"}]}""", 0));
        InboundInterbankRequest r = inbound();

        SagaInstance row = inboundSaga.run(r, "corr");

        assertThat(row.getState()).isEqualTo(SagaState.FAILED);
        assertThat(row.getFailureCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        assertThat(stages()).hasSize(8).endsWith("7 AUTHORIZED PASSED PROCESSING", "8 POSTED FAILED FAILED");
    }

    @Test
    void inboundInterbank_ledgerRefusesTheCall_failsWithItsCode() {
        STUBS.put(POSTINGS, new Stub(422, example("common-error.json"), 0));
        InboundInterbankRequest r = inbound();

        SagaInstance row = inboundSaga.run(r, "corr");

        assertThat(row.getState()).isEqualTo(SagaState.FAILED);
        assertThat(row.getFailureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(stages()).endsWith("8 POSTED FAILED FAILED");
    }

    @Test
    void inboundInterbank_answerThatNamesNoOutcome_isNotTakenForAPosting() {
        STUBS.put(POSTINGS, new Stub(200, "{\"posted\":0,\"failed\":0,\"results\":[]}", 0));
        InboundInterbankRequest r = inbound();

        SagaInstance row = inboundSaga.run(r, "corr");

        assertThat(row.getState()).isEqualTo(SagaState.FAILED);
        assertThat(row.getFailureCode()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    void inboundInterbank_ledgerGivesNoAnswer_staysStartedAndTheRepeatIsTheSamePosting() {
        STUBS.put(POSTINGS, new Stub(503, "{}", 0));
        InboundInterbankRequest r = inbound();

        assertThatThrownBy(() -> inboundSaga.run(r, "corr")).isInstanceOf(DownstreamException.class);

        // Nothing is claimed either way: no stage event, and the row waits for the partner to repeat.
        assertThat(sagas.findByTransactionId(r.transactionId()).orElseThrow().getState()).isEqualTo(SagaState.STARTED);
        assertThat(events()).isEmpty();

        STUBS.put(POSTINGS, POSTED_ONCE);
        SagaInstance row = inboundSaga.run(r, "corr");

        assertThat(row.getState()).isEqualTo(SagaState.COMPLETED);
        // The same key both times, so the ledger sees one posting however often it is asked.
        assertThat(CALLS).extracting(Call::path, Call::idempotencyKey).containsExactly(
                org.assertj.core.groups.Tuple.tuple(POSTINGS, r.transactionId() + ":posting"),
                org.assertj.core.groups.Tuple.tuple(POSTINGS, r.transactionId() + ":posting"));
        assertThat(stages()).hasSize(11);
    }

    @Test
    void inboundEndpoint_turnsALedgerSilenceInto503AndAnswersTheStateOnceItPosts() throws Exception {
        String service = tokens.generateToken(0L, "transaction-service", List.of("ROLE_SERVICE"));
        InboundInterbankRequest r = inbound();
        String body = jsonMapper.writeValueAsString(r);

        mockMvc.perform(post(INBOUND).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        STUBS.put(POSTINGS, new Stub(503, "{}", 0));
        mockMvc.perform(post(INBOUND).header("Authorization", "Bearer " + service)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("LEDGER_UNAVAILABLE"));

        STUBS.put(POSTINGS, POSTED_ONCE);
        mockMvc.perform(post(INBOUND).header("Authorization", "Bearer " + service)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(r.transactionId().toString()))
                .andExpect(jsonPath("$.sagaState").value("COMPLETED"));
    }

    // --- recovery and reversal: the remaining branches ---

    @Test
    void recovery_aPayloadThatCannotBeRead_isSkippedAndTheOthersStillRun() {
        UUID brokenId = UUID.randomUUID();
        SagaInstance broken = sagas.findOrCreate(brokenId, "TX-BROKEN-PAYLOAD", "{this is not json");
        broken.setState(SagaState.RISK_APPROVED);
        sagas.saveAndFlush(broken);
        StartTransferRequest t = riskApproved(Duration.ZERO);
        runTasksInline();

        recovery.run(null);

        assertThat(row(t).getState()).isEqualTo(SagaState.POSTED);
        SagaInstance stillThere = sagas.findByTransactionId(brokenId).orElseThrow();
        assertThat(stillThere.getState()).isEqualTo(SagaState.RISK_APPROVED);

        // Taken out of flight so that it is not picked up by the recovery of another test.
        stillThere.setState(SagaState.FAILED);
        sagas.saveAndFlush(stillThere);
    }

    @Test
    void reversalOfATransferThatNeverReachedTheLedger_cancelsItWithoutAnyLedgerCall() {
        UUID origId = UUID.randomUUID();
        SagaInstance original = sagas.findOrCreate(origId);
        original.setReferenceNo("TX-NOT-HELD-001");
        original.setState(SagaState.RISK_APPROVED);
        sagas.saveAndFlush(original);
        UUID revId = UUID.randomUUID();

        reversalSaga.run(new StartReversalRequest(revId, origId, "RV-NOT-HELD-001", "Sent by mistake", 3L), "corr");

        SagaInstance cancelled = sagas.findByTransactionId(origId).orElseThrow();
        assertThat(cancelled.getState()).isEqualTo(SagaState.FAILED);
        assertThat(cancelled.getFailureCode()).isEqualTo(ReversalSaga.CANCELLED_BEFORE_HOLD);
        assertThat(sagas.findByTransactionId(revId).orElseThrow().getState()).isEqualTo(SagaState.COMPLETED);
        // No hold was ever placed, so there is nothing to ask the ledger to undo.
        assertThat(ledgerCalls()).isEmpty();
    }

    @Test
    void reversalOfATransferThatAlreadyFailed_isRefusedWithoutAnyLedgerCall() {
        UUID origId = UUID.randomUUID();
        SagaInstance original = sagas.findOrCreate(origId);
        original.setReferenceNo("TX-FAILED-001");
        original.setState(SagaState.FAILED);
        original.setFailureCode("INSUFFICIENT_FUNDS");
        sagas.saveAndFlush(original);
        UUID revId = UUID.randomUUID();

        reversalSaga.run(new StartReversalRequest(revId, origId, "RV-FAILED-001", "Dispute", 3L), "corr");

        SagaInstance reversal = sagas.findByTransactionId(revId).orElseThrow();
        assertThat(reversal.getState()).isEqualTo(SagaState.FAILED);
        assertThat(reversal.getFailureCode()).isEqualTo("ALREADY_TERMINAL");
        // The transfer moved no money, so it keeps its own outcome and the ledger is not asked.
        assertThat(sagas.findByTransactionId(origId).orElseThrow().getFailureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(ledgerCalls()).isEmpty();
    }

    @Test
    void sameReversalRunTwice_reversesOnce() {
        UUID origId = UUID.randomUUID();
        SagaInstance original = sagas.findOrCreate(origId);
        original.setReferenceNo("TX-20261003-8F3A21C0");
        original.setState(SagaState.POSTED);
        sagas.saveAndFlush(original);
        StartReversalRequest r = new StartReversalRequest(
                UUID.randomUUID(), origId, "RV-TWICE-001", "Customer dispute approved", 3L);

        reversalSaga.run(r, "corr");
        reversalSaga.run(r, "corr");

        assertThat(sagas.findByTransactionId(r.transactionId()).orElseThrow().getState()).isEqualTo(SagaState.COMPLETED);
        assertThat(ledgerCalls()).extracting(Call::path).containsExactly(REVERSAL);
    }

    /** The O4 contract example with a fresh transaction ID, so each test has its own saga row. */
    private InboundInterbankRequest inbound() {
        InboundInterbankRequest e = jsonMapper.readValue(example("O4-inbound-interbank-saga.request.json"),
                InboundInterbankRequest.class);
        return new InboundInterbankRequest(UUID.randomUUID(), e.referenceNo(), e.messageId(), e.sourceBankCode(),
                e.settlementAccountId(), e.destinationAccountId(), e.amount(), e.currency());
    }

    private StartTransferRequest transfer() {
        StartTransferRequest e = jsonMapper.readValue(example("O1-start-transfer-saga.request.json"),
                StartTransferRequest.class);
        return new StartTransferRequest(UUID.randomUUID(), e.referenceNo(), e.channel(), e.customerId(),
                e.sourceAccountId(), e.destinationAccountId(), e.destinationBankCode(), e.amount(), e.currency(),
                e.actorId(), e.actorType(), e.isNewBeneficiary());
    }

    private StartTransferRequest transferTo(String destinationBankCode) {
        StartTransferRequest t = transfer();
        return new StartTransferRequest(t.transactionId(), t.referenceNo(), t.channel(), t.customerId(),
                t.sourceAccountId(), t.destinationAccountId(), destinationBankCode, t.amount(), t.currency(),
                t.actorId(), t.actorType(), t.isNewBeneficiary());
    }

    private StartTransferRequest transferWithNewBeneficiary(Boolean isNewBeneficiary) {
        StartTransferRequest t = transfer();
        return new StartTransferRequest(t.transactionId(), t.referenceNo(), t.channel(), t.customerId(),
                t.sourceAccountId(), t.destinationAccountId(), t.destinationBankCode(), t.amount(), t.currency(),
                t.actorId(), t.actorType(), isNewBeneficiary);
    }

    /** How often the risk engine was told (R2) that this transfer did not post. */
    private long limitReleases(UUID transactionId) {
        return outcomes(transactionId).stream().filter(o -> "FAILED".equals(o.path("outcome").asString())).count();
    }

    /** Every R2 report about this transfer, in the order sent. */
    private List<JsonNode> outcomes(UUID transactionId) {
        return BODIES.stream().filter(body -> body.startsWith(OUTCOMES + " "))
                .map(body -> jsonMapper.readTree(body.substring(OUTCOMES.length() + 1)))
                .filter(outcome -> transactionId.toString().equals(outcome.path("transactionId").asString()))
                .toList();
    }

    /** What the risk engine was asked about this transfer. Recovery may run other sagas as well. */
    private JsonNode riskRequest(StartTransferRequest t) {
        return BODIES.stream().filter(body -> body.startsWith(RISK + " "))
                .map(body -> jsonMapper.readTree(body.substring(RISK.length() + 1)))
                .filter(request -> t.transactionId().toString().equals(request.path("transactionId").asString()))
                .reduce((first, last) -> last).orElseThrow();
    }

    /** Recovery hands sagas to the executor, which is a mock; this makes it run them on the calling thread. */
    private void runTasksInline() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(applicationTaskExecutor).execute(any(Runnable.class));
    }

    /** A transfer whose saga has been waiting at RISK_APPROVED for this long. */
    private StartTransferRequest riskApproved(Duration waiting) {
        StartTransferRequest t = transfer();
        SagaInstance row = sagas.findOrCreate(t.transactionId(), t.referenceNo(), jsonMapper.writeValueAsString(t));
        row.setState(SagaState.RISK_APPROVED);
        sagas.saveAndFlush(row);
        lastWritten(t, waiting);
        return t;
    }

    /** Ages a saga row. A save cannot do it, because every save stamps updated_at with the current time. */
    private void lastWritten(StartTransferRequest t, Duration ago) {
        jdbc.update("update saga_instance set updated_at = ? where transaction_id = ?",
                Timestamp.from(Instant.now().minus(ago)), t.transactionId());
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
                BODIES.add(path + " " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                CALLS.add(new Call(path, exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("X-Correlation-ID")));
                Runnable meanwhile = MEANWHILE.remove(path);
                if (meanwhile != null) {
                    meanwhile.run();
                }
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
