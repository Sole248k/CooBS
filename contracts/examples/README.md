# Contract examples

> Copies of the examples in [`../api/FUTURE_INTEGRATED_API_CONTRACT.md`](../api/FUTURE_INTEGRATED_API_CONTRACT.md). The contract is the source: do not edit these files by hand.

One file per example in the contract. File names start with the endpoint ID used in the contract (`T1`, `L3`, ...), `common-` for shared shapes, or `event-` for Kafka payloads. IDs and names are the seeded ones in `database/seed/SEED_REFERENCE.md`.

Dev 7 curates these into `app/assets/fixtures/` (handshake H15). To change one, ask Dev 1: the contract is changed first, and Dev 1 re-exports the files from it. The marker above each JSON block in the contract names its file.

| File | Contract section |
| --- | --- |
| `common-token-claims.customer.json` | 2.3 Token claims (unchanged) |
| `common-token-claims.service.json` | 2.3 Token claims (unchanged) |
| `common-error.json` | 2.5 Errors |
| `common-page.json` | 2.6 Paging |
| `A2-A4-login.request.json` | 4. auth-service (8081) |
| `A2-A4-login.response.json` | 4. auth-service (8081) |
| `A3-A5-mfa-verify.request.json` | 4. auth-service (8081) |
| `A6-token-refresh.request.json` | 4. auth-service (8081) |
| `A8-token-me.response.json` | 4. auth-service (8081) |
| `C1-C2-customer-profile.response.json` | 5. customer-service (8089) — NEW |
| `C3-customer-360.response.json` | 5. customer-service (8089) — NEW |
| `C5-add-beneficiary.request.json` | 5. customer-service (8089) — NEW |
| `C7-risk-profile.response.json` | 5. customer-service (8089) — NEW |
| `K3-open-account.request.json` | 6. account-service (8082) |
| `K3-K4-account.response.json` | 6. account-service (8082) |
| `K6-K7-account-lookup.response.json` | 6. account-service (8082) |
| `T1-transfer.request.json` | 7. transaction-service (8090) — NEW |
| `T1-transfer.response.json` | 7. transaction-service (8090) — NEW |
| `T2-transaction.response.json` | 7. transaction-service (8090) — NEW |
| `T3-timeline-staff.response.json` | 7. transaction-service (8090) — NEW |
| `T3-timeline-customer.response.json` | 7. transaction-service (8090) — NEW |
| `T5-reversal.request.json` | 7. transaction-service (8090) — NEW |
| `T5-reversal.response.json` | 7. transaction-service (8090) — NEW |
| `M1-mobile-dashboard.response.json` | 7.1 mobile-bff (8094) — NEW, mobile backend-for-frontend |
| `M2-mobile-timeline.response.json` | 7.1 mobile-bff (8094) — NEW, mobile backend-for-frontend |
| `M3-mobile-remittance.request.json` | 7.1 mobile-bff (8094) — NEW, mobile backend-for-frontend |
| `M3-mobile-remittance.response.json` | 7.1 mobile-bff (8094) — NEW, mobile backend-for-frontend |
| `O1-start-transfer-saga.request.json` | 8. orchestration-service (8091) — NEW, internal only |
| `O1-start-transfer-saga.response.json` | 8. orchestration-service (8091) — NEW, internal only |
| `O2-start-reversal-saga.request.json` | 8. orchestration-service (8091) — NEW, internal only |
| `O3-saga-state.response.json` | 8. orchestration-service (8091) — NEW, internal only |
| `O4-inbound-interbank-saga.request.json` | 8. orchestration-service (8091) — NEW, internal only |
| `O4-inbound-interbank-saga.response.json` | 8. orchestration-service (8091) — NEW, internal only |
| `R1-risk-evaluate.request.json` | 9. risk-engine (8092) — NEW, internal only |
| `R1-risk-evaluate.response.json` | 9. risk-engine (8092) — NEW, internal only |
| `R2-risk-outcome.request.json` | 9. risk-engine (8092) — NEW, internal only |
| `R2-risk-outcome.response.json` | 9. risk-engine (8092) — NEW, internal only |
| `R2-risk-outcome-reversed.request.json` | 9. risk-engine (8092) — NEW, internal only |
| `R2-risk-outcome-reversed.response.json` | 9. risk-engine (8092) — NEW, internal only |
| `L-S1-balance.response.json` | 10.1 Staff endpoints (existing, staff roles only) |
| `L1-open-balance.request.json` | 10.2 Internal endpoints (NEW) |
| `L1-open-balance.response.json` | 10.2 Internal endpoints (NEW) |
| `L3-place-hold.request.json` | 10.2 Internal endpoints (NEW) |
| `L3-place-hold.response.json` | 10.2 Internal endpoints (NEW) |
| `L4-capture-hold.request.json` | 10.2 Internal endpoints (NEW) |
| `L4-capture-hold.response.json` | 10.2 Internal endpoints (NEW) |
| `L5-release-hold.request.json` | 10.2 Internal endpoints (NEW) |
| `L5-release-hold.response.json` | 10.2 Internal endpoints (NEW) |
| `L7-reversal.request.json` | 10.2 Internal endpoints (NEW) |
| `L7-reversal.response.json` | 10.2 Internal endpoints (NEW) |
| `L8-postings.request.json` | 10.2 Internal endpoints (NEW) |
| `L8-postings.response.json` | 10.2 Internal endpoints (NEW) |
| `L9-trial-balance.response.json` | 10.2 Internal endpoints (NEW) |
| `B1-eod-trigger.request.json` | 11. batch-service (8093) — NEW |
| `B1-eod-trigger.response.json` | 11. batch-service (8093) — NEW |
| `B2-eod-run.response.json` | 11. batch-service (8093) — NEW |
| `N1-notifications.response.json` | 12. notification-service (8084) and audit-service (8085) |
| `N2-mark-read.response.json` | 12. notification-service (8084) and audit-service (8085) |
| `N3-unread-count.response.json` | 12. notification-service (8084) and audit-service (8085) |
| `N4-dlq-item.json` | 12. notification-service (8084) and audit-service (8085) |
| `N5-dlq-replay.response.json` | 12. notification-service (8084) and audit-service (8085) |
| `U1-statement-row.json` | 12. notification-service (8084) and audit-service (8085) |
| `U3-verify-chain.response.json` | 12. notification-service (8084) and audit-service (8085) |
| `U4-daily-seal.response.json` | 12. notification-service (8084) and audit-service (8085) |
| `event-envelope.json` | 13. Events (Kafka) |
| `event-transaction-stage-changed.payload.json` | 13. Events (Kafka) |
| `event-ledger-transfer-completed.payload.json` | 13. Events (Kafka) |
| `event-notification-dispatched.payload.json` | 13. Events (Kafka) |
| `event-batch-eod-step-completed.payload.json` | 13. Events (Kafka) |
| `I4-interbank-outbound.request.json` | 14. Inter-bank transfers |
| `I4-interbank-outbound.response.json` | 14. Inter-bank transfers |
