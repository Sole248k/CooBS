# 9. Events and messaging

## 9.1 Broker

Apache Kafka in KRaft mode, one broker (`kafka-broker`), PLAINTEXT listeners. Locally: `localhost:9092` from the host, `kafka-broker:29092` inside the Docker network. In Azure: a StatefulSet in the cluster with an 8 GB disk, reached from the on-premises VM through an internal load balancer at `10.240.15.200:9094`.

Topics are created at start-up by the `kafka-init-topics` container (and a Job in Azure): 3 partitions, replication factor 1. Kafka UI runs at `http://localhost:8088` (cluster `local-cluster`).

## 9.2 Envelope

New events share one envelope (contract §13):

```json
{
  "eventId": "evt_3b1c…",
  "eventType": "TRANSACTION_STAGE_CHANGED",
  "timestamp": "2026-10-03T11:40:00.060Z",
  "version": "1.0",
  "correlationId": "e7b8c1…",
  "payload": { }
}
```

`notification-service` also accepts the older flat payloads that `ledger-service` and `account-service` publish. Consumers deduplicate on `eventId`.

## 9.3 Topics

| Topic | Producer | Consumers | Key | Payload highlights |
| --- | --- | --- | --- | --- |
| `ledger.mutation.completed.v1` | `ledger-service` (each debit/credit leg); `batch-service` (month-end/daily interest notifications) | `notification-service` | Account ID | `accountId`, `customerId`, `type` (DEBIT/CREDIT), `amount`, `newBalance`, `referenceNo` |
| `ledger.transfer.completed.v1` | `ledger-service` via outbox | `notification-service`, `orchestration-service` (stage 9) | Source account / transaction | `transferReference`, `transactionId`, source/destination IDs, previous and new balances, customer IDs, `auditHash` |
| `ledger.transfer.failed.v1` | `ledger-service` (direct `/transfers` refusal) | `notification-service` | Source account | Reference, reason |
| `account.created.v1` | `account-service` | — (published for consumers to come) | Account ID | Account, customer, type |
| `account.closure.completed.v1` | `account-service` | `notification-service` | Account ID | Account, customer, approver |
| `kyc.request.submitted.v1` | — (see 9.6) | `notification-service` | Customer ID | Request ID, customer |
| `kyc.request.evaluated.v1` | — (see 9.6) | `notification-service` | Customer ID | Decision, reason |
| `transaction.stage.changed.v1` | `transaction-service` (1–3), `orchestration-service` (4–11) | `transaction-service` (timeline, status) | Transaction ID | `transactionId`, `referenceNo`, `stageNo`, `stageName`, `outcome`, `transactionStatus`, `failureCode`, `detail`, `occurredAt` |
| `notification.dispatched.v1` | `notification-service` | `orchestration-service` (stages 10, 11 → `COMPLETED`) | Transaction / reference | `transactionId`, `referenceNo`, `sourceEventId`, `customerIds`, `channel`, `dispatchedAt` |
| `batch.eod.step.completed.v1` | `batch-service` | — (observability) | Run ID | `batchRunId`, `businessDate`, `stepNo`, `stepName`, `status`, `itemsProcessed`, `totalAmount`, `durationMs` |

Dead-letter topics: every topic above except `batch.eod.step.completed.v1` has a `<topic>.DLT` twin (nine), plus `notification.events.DLT` — 20 topics in all.

## 9.4 Delivery guarantees

| Producer | Settings | Guarantee |
| --- | --- | --- |
| `ledger-service` | `acks=all`, idempotent producer, 3 retries, outbox relay stamps a row only after ack | At-least-once; no event lost on broker or link outage |
| `account-service` | `acks=all`, idempotent | Best effort after commit (a send failure is logged) |
| `transaction-service` | Published after the DB commit | Best effort; the stage rows are already in its own DB |
| `orchestration-service` | Saga row saved before publishing; a failed send is logged and dropped (`StageEvents`) | Best effort; never blocks the saga |
| `batch-service` | `acks=1`, `max.block.ms=3000`, can be disabled (`BATCH_KAFKA_ENABLED`) | Best effort; never fails a step |

| Consumer | Group | Retry and dead letter |
| --- | --- | --- |
| `notification-service` | `notification-service-group`, `auto-offset-reset=earliest` | 3 attempts, exponential back-off 1 s × 2.0, then `notification.events.DLT` with headers `x-original-topic`, `x-exception-message`, `x-attempts`, `x-failed-at` |
| `transaction-service` | `transaction-service-group` | Back-off 1 s, 2 s, 4 s, then `<topic>.DLT` |
| `orchestration-service` | `orchestration-service-group` | Exponential back-off, then `<topic>.DLT` |

## 9.5 Dead-letter inspection and replay

- `GET /api/v1/notifications/admin/dlq` (N4) reads `notification.events.DLT` and returns each record with its original topic, failure message, attempts and payload.
- `POST /api/v1/notifications/admin/dlq/{recordId}/replay` (N5) republishes one record to its original topic; `POST /admin/dlq/replay` replays all.
- The staff console's **Dead Letter Queue** screen (admin only) calls these through `staff-bff`; the admin overview shows the DLQ count.
- `scripts/test_dead_letter_replay.ps1` proves the path end to end: it stops the notification database, sends events that therefore fail into the DLT, restarts the database and replays them.

## 9.6 Gaps

- No producer publishes `kyc.request.submitted.v1` or `kyc.request.evaluated.v1` any more: KYC moved from `account-service` to `customer-service`, and `customer-service` has no Kafka producer. `account-service` still carries the topic names in its properties. KYC notifications described in the root `README.md` therefore do not occur. See [section 14](14-known-gaps-and-limitations.md).
- `account.created.v1` and `batch.eod.step.completed.v1` have no consumer.
- The per-topic `.DLT` twins of the notification topics are unused; notification failures all go to `notification.events.DLT`.
