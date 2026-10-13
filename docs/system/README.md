# CooBS System Documentation

**System:** CooBS — Cooperative Banking System (Group 2, FSE Capstone)
**Describes:** the code on branch `dev` at commit `1006719` (13 October 2026) and the Azure deployment of 10–11 October 2026
**Audience:** the development team, evaluators and technical reviewers, and engineers joining the project

This folder is the single, complete reference for the system as it is built. Where older documents in the repository disagree with the code (the root `README.md`, `contracts/api/CURRENT_API_INVENTORY_AND_USAGE_GUIDE.md`, `docs/TEAM_STATUS.md`), this set follows the code. Section 14 lists those disagreements.

## Contents

| # | Document | What it answers |
| --- | --- | --- |
| 1 | [System overview](01-system-overview.md) | What CooBS is, who uses it, what it does, glossary, team |
| 2 | [Architecture](02-architecture.md) | Hybrid cloud/on-premises design, principles, components, request paths, time limits |
| 3 | [Service catalogue](03-service-catalog.md) | Each of the 13 backend services: purpose, port, zone, data, dependencies, behaviour |
| 4 | [API reference](04-api-reference.md) | Every endpoint, its roles, gateway routes, rate limits, headers, error format |
| 5 | [Transfer lifecycle](05-transfer-lifecycle.md) | The transfer saga, the 11 stages, reversals, inter-bank in and out, idempotency |
| 6 | [Risk engine](06-risk-engine.md) | Fraud scoring model, decision bands, daily limits, velocity counters |
| 7 | [Ledger, audit and end of day](07-ledger-audit-and-eod.md) | Double-entry posting, holds, outbox, SHA-256 hash chain, daily seal, EOD batch |
| 8 | [Data model](08-data-model.md) | Databases, schemas, key tables, constraints, GL accounts, seed data |
| 9 | [Events and messaging](09-events-and-messaging.md) | Kafka topics, envelope, producers and consumers, dead letters and replay |
| 10 | [Security](10-security.md) | Authentication, tokens, roles, service identity, inter-bank signing, network controls |
| 11 | [Client applications](11-client-applications.md) | React staff console and Flutter member app |
| 12 | [Deployment and operations](12-deployment-and-operations.md) | Local stack, configuration, Azure deployment, scripts, observability, runbook |
| 13 | [Testing and quality](13-testing-and-quality.md) | Unit tests, E2E suite, scenario scripts, load test, CI pipelines |
| 14 | [Known gaps and limitations](14-known-gaps-and-limitations.md) | What is incomplete, risky or out of date, with recommended fixes |

## One-paragraph summary

CooBS is a core banking platform for a cooperative. Members use a Flutter mobile app; tellers and administrators use a React staff console. Behind a single Spring Cloud Gateway sit thirteen Spring Boot services split across two zones: nine run in the cloud (sign-in, routing, customer data, transfer intake, saga orchestration, fraud scoring, notifications and two backend-for-frontend services) and four run on-premises beside the system of record (accounts, the double-entry ledger, the audit store and the end-of-day batch). A transfer is accepted by `transaction-service`, run as a saga by `orchestration-service` (risk check → hold → capture → events), and posted by `ledger-service` with ordered row locks in Oracle. Every posting writes a row to an append-only PostgreSQL audit table whose triggers chain each row to the last with SHA-256. Kafka carries ten event topics and their dead-letter twins. The system runs locally on Docker Compose and in Azure on AKS plus a VM that stands in for the bank's datacenter, served at `https://coobs.indonesiacentral.cloudapp.azure.com`.

## Related source documents

These remain useful and are referenced throughout:

| Document | Status |
| --- | --- |
| `contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md` (tag `contracts-v1`) | The frozen API contract. Endpoint IDs (A1, T1, O1, L4…) used here come from it |
| `contracts/api/openapi.yaml`, `contracts/examples/` | OpenAPI spec and 61 JSON examples, one per endpoint |
| `contracts/runtime/RUNTIME_SETTINGS.md` | Ports, environment variable names, database names |
| `integrated-capstone-plans/ENTERPRISE_ARCHITECTURE_AND_DEPLOYMENT.md` | Planned vs deployed architecture, gap register G1–G36 |
| `integrated-capstone-plans/AZURE_DEPLOYMENT_AS_BUILT.md` | Record of the Azure deployment and the 15 problems met |
| `infra/README.md` | Runbook to deploy Azure from nothing |
| `database/seed/SEED_REFERENCE.md` | Every seeded member, account and scenario (generated) |
| `docs/docs-services/ENTERPRISE_OBSERVABILITY_GUIDE.md` | Local Prometheus/Grafana/Loki runbook |
