package com.group2.fse.transaction_service.service.impl;

import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sends O1 again for a transfer whose saga never showed a sign of life.
 *
 * T1 saves the transaction and starts the saga in the background. If that one call is lost
 * (orchestration-service down, a timeout, this service restarting) the row would stay
 * PROCESSING for ever with no saga behind it. A transfer that is still PROCESSING with no
 * stage above 3 after a short wait gets its start sent again. O1 is idempotent on the
 * transaction ID, so a start that did arrive is not run twice.
 */
@Slf4j
@Component
public class SagaStartRedispatcher {

    private final TxnTransactionRepository transactionRepository;
    private final OrchestrationClient orchestrationClient;
    private final Duration waitBeforeRetry;
    private final Duration giveUpAfter;

    public SagaStartRedispatcher(TxnTransactionRepository transactionRepository,
                                 OrchestrationClient orchestrationClient,
                                 @Value("${app.saga-start.redispatch-after:30s}") Duration waitBeforeRetry,
                                 @Value("${app.saga-start.give-up-after:15m}") Duration giveUpAfter) {
        this.transactionRepository = transactionRepository;
        this.orchestrationClient = orchestrationClient;
        this.waitBeforeRetry = waitBeforeRetry;
        this.giveUpAfter = giveUpAfter;
    }

    @Scheduled(initialDelayString = "${app.saga-start.redispatch-interval:30s}",
            fixedDelayString = "${app.saga-start.redispatch-interval:30s}")
    public void redispatch() {
        redispatch(Instant.now());
    }

    void redispatch(Instant now) {
        List<TxnTransaction> waiting = transactionRepository.findProcessingWithoutSagaStage(
                now.minus(giveUpAfter), now.minus(waitBeforeRetry));
        for (TxnTransaction txn : waiting) {
            if (txn.getCustomerId() == null || txn.getDestinationAccountId() == null) {
                // Saved before T1 resolved both accounts; there is nothing safe to send.
                continue;
            }
            log.warn("Transfer {} has no saga stage yet; sending O1 again", txn.getTransactionId());
            boolean staff = "TELLER".equals(txn.getChannel());
            // The original actor is not stored on the row, so the account owner stands in.
            orchestrationClient.triggerTransferSaga(
                    TransferServiceImpl.sagaRequest(txn, txn.getCustomerId(), staff ? "STAFF" : "CUSTOMER"),
                    UUID.randomUUID().toString());
        }
    }
}
