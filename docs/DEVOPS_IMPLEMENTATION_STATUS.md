# DevOps, Cloud Architecture & Perimeter Infrastructure — Implementation Status

**Author:** Jared (Dev 8 — DevOps & Cloud Engineer)  
**Target Audience:** CooBS Engineering Team (Carl, Hans, Wayne, Ysa, April, Neo, Gabo)  
**Status:** Implemented & Verified in Local Running Stack  
**Primary Repository Path:** `Group2_FSE_Capstone/`  

---

## Executive Summary

This document provides a comprehensive handover and status summary of the cloud architecture, database reliability, container networking, edge routing, and perimeter DevSecOps infrastructure implemented on the DevOps track.

All infrastructure layers—including the polyglot data tier (Oracle Free, PostgreSQL, Redis, Kafka KRaft), unified Spring Cloud Gateway edge ingress, Redis token-bucket rate limiting (HTTP 429), loopback network perimeter isolation, CORS preflight policies, and test automation harnesses—are fully deployed, tested, and passing with 100% green health checks.

---

## 1. Cloud Architecture & Sizing Deliverables

### A. Event Streaming Head: Azure Event Hubs (Kafka Surface)
- **Architectural Decision:** Standard Azure Event Hubs with Apache Kafka API compatibility selected as our cloud streaming baseline.
- **Key Trade-offs Evaluated:**
  * **Zero Operational Overhead:** Eliminates cluster, broker, and KRaft controller maintenance compared to self-hosted Strimzi Kafka on AKS.
  * **Code Parity:** Direct compatibility with existing Spring Cloud Stream and Kafka clients (`kafka-clients:3.x`) using SASL/PLAIN connection strings. Microservice code requires zero modification between local and Azure environments.
  * **Local Parity:** Local development runs lightweight Apache Kafka in KRaft mode, while Azure deployments point directly to Event Hubs.

### B. Azure Cloud Sizing & Cost Model
- **Itemized Sizing Model Documented:** Complete architecture and cost breakdown available in [docs/CLOUD_DECISIONS_AND_AZURE_COST_MODEL.md](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/docs/CLOUD_DECISIONS_AND_AZURE_COST_MODEL.md).
- **Target Cost Envelope:**
  * **1-Week Capstone Evaluation Window:** **$95.43** total spend with automated compute node shutdown outside defense/testing hours.
  * **Full Month Production Baseline:** **$428.02/month** (AKS 3x `Standard_D4s_v5`, Azure Database for PostgreSQL Flexible Server, Azure Cache for Redis C1, Azure Bastion, and Container Registry).

---

## 2. Multi-Tier Database Health & Volume Isolation

To eliminate container startup race conditions and database socket starvation during local runs and automated test executions:

### A. Healthcheck Specifications Audit
| Container Name | Service Role | Healthcheck Mechanism | Interval / Timeout | Retries |
| :--- | :--- | :--- | :---: | :---: |
| `oracle-core-db` | Master System of Record (SoR) | `/opt/oracle/healthcheck.sh` (`SELECT 1 FROM dual;`) | 20s / 15s | 20 (60s start period) |
| `postgres-audit-db` | Immutable Forensic Audit Store | `pg_isready -U postgres -d audit_store` | 5s / 5s | 10 |
| `postgres-cloud-db` | Decomposed Identity & Txn Store | `pg_isready -U postgres -d identity_db` | 5s / 5s | 10 |
| `redis-cache` | Distributed Idempotency & Balance Cache | `redis-cli ping` | 5s / 3s | 5 |
| `kafka-broker` | Single Broker KRaft Event Streaming | `/opt/kafka/bin/kafka-broker-api-versions.sh` | 10s / 5s | 15 (15s start period) |

### B. Named Volume Persistence
All persistent stores use dedicated Docker volumes to ensure transactional ledgers, audit hash chains, and idempotency locks survive container stop and teardown cycles (`docker compose down` without `-v`):
- `oracle_data` $\rightarrow$ `/opt/oracle/oradata`
- `postgres_data` $\rightarrow$ `/var/lib/postgresql/data`
- `postgres_cloud_data` $\rightarrow$ `/var/lib/postgresql/data`
- `redis_data` $\rightarrow$ `/data`
- `kafka_data` $\rightarrow$ `/var/lib/kafka/data`

---

## 3. High-Concurrency Connection Pool Hardening (HikariCP)

Under heavy concurrency (e.g., JMeter benchmark testing), unconstrained connection pools cause database connection exhaustion, slow query thread stalls, and HTTP 500 errors. 

Standardized HikariCP environment parameters are configured in [docker-compose.yml](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/docker-compose.yml) across all database-connected microservices:

```yaml
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 20
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: 5
SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT: 5000       # Fail fast in 5s rather than stall threads
SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT: 300000            # Evict idle connections after 5m
SPRING_DATASOURCE_HIKARI_MAX_LIFETIME: 1800000           # Refresh connections after 30m
SPRING_DATASOURCE_HIKARI_LEAK_DETECTION_THRESHOLD: 15000 # Detect queries holding locks > 15s
```

**Benefits:**
1. Guarantees that total pooled connections across microservices will not breach Oracle's `PROCESSES / SESSIONS` ceiling (`ORA-00018`).
2. Provides immediate log alerts with stack traces whenever a database transaction holds a lock longer than 15 seconds.

---

## 4. Network Perimeter Isolation & Ingress Security (Pillar 3 Rubric)

To meet **Pillar 3 (Cloud & DevSecOps Perimeter)** of the Capstone Evaluation Rubric, all external traffic must be strictly routed through the Spring Cloud API Gateway (Port 8080). Internal backend microservices must reject or be inaccessible from unauthorized direct perimeter calls.

```
[ Omnichannel Clients (Flutter Mobile / React Web / Postman) ]
                              │
                              ▼ (Port 8080 ONLY - Public Ingress)
   ┌─────────────────────────────────────────────────────────────┐
   │             SPRING CLOUD API GATEWAY (Port 8080)            │
   │  - Perimeter JWT Verification & Route Authentication        │
   │  - Internal Route Blocker (HTTP 403 Forbidden on /internal) │
   │  - Redis Token-Bucket Rate Limiter (HTTP 429)               │
   │  - Dynamic CORS Preflight Policy (Web & Native Mobile)       │
   │  - Correlation ID Injection (X-Correlation-ID)              │
   └──────────────────────────┬──────────────────────────────────┘
                              │
            Docker Bridge Network (`core-banking-net`)
                              │
   ┌──────────────────┬───────┴──────────┬──────────────────┐
   ▼                  ▼                  ▼                  ▼
[ auth-service ] [ ledger-service ] [ customer-service ] [ txn-service ]
(127.0.0.1:8081) (127.0.0.1:8083)   (127.0.0.1:8089)     (127.0.0.1:8090)
```

### A. Host Port Binding Hardening
In [docker-compose.yml](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/docker-compose.yml), all backend microservice and database ports are bound to `127.0.0.1` (loopback only) instead of `0.0.0.0`. 
- **Only Port 8080 (`api-gateway`)** is published on all network interfaces (`0.0.0.0:8080`).
- Devices on the local physical network cannot bypass the Gateway to call microservices directly.
- Developers can still access individual services on `localhost` for isolated debugging.

### B. Internal Route Blocker (`InternalPerimeterRejectionFilter.java`)
Implemented a high-precedence WebFlux filter on the API Gateway ([InternalPerimeterRejectionFilter.java](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/backend/api-gateway/src/main/java/com/group2/fse/api_gateway/filter/InternalPerimeterRejectionFilter.java)):
- Any external request reaching the Gateway targeting internal-only routes (e.g., `/internal/**`, such as `/internal/v1/risk/evaluate` or `/internal/v1/sagas/**`) is immediately intercepted and rejected with **HTTP 403 Forbidden**.
- Rejections produce a standardized RFC-7807 Problem Detail response with `X-Correlation-ID` tracking.
- Tested and verified in [GatewayRoutingIntegrationTest.java](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/backend/api-gateway/src/test/java/com/group2/fse/api_gateway/route/GatewayRoutingIntegrationTest.java).

---

## 5. API Gateway Ingress Routing & Service Decomposition

In [backend/api-gateway/src/main/resources/application.yml](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/backend/api-gateway/src/main/resources/application.yml), the Gateway provides unified routing across all core and decomposed microservices:

| Route ID | Ingress Path Predicate | Target Service & Port | Key Resolver | Replenish / Burst |
| :--- | :--- | :--- | :--- | :---: |
| `auth-service-route` | `/api/v1/auth/**` | `auth-service:8081` | `ipKeyResolver` | 10 / 20 req/s |
| `account-service-accounts-route` | `/api/v1/accounts/**` | `account-service:8082` | `userOrIpKeyResolver` | 50 / 100 req/s |
| `customer-service-route` | `/api/v1/customers/**` | `customer-service:8089` | `userOrIpKeyResolver` | 50 / 100 req/s |
| `ledger-service-route` | `/api/v1/ledger/**` | `ledger-service:8083` | `userOrIpKeyResolver` | 20 / 40 req/s |
| `notification-service-route` | `/api/v1/notifications/**` | `notification-service:8084` | `userOrIpKeyResolver` | 50 / 100 req/s |
| `audit-service-route` | `/api/v1/audit/**` | `audit-service:8085` | `userOrIpKeyResolver` | 30 / 60 req/s |
| `transaction-service-route` | `/api/v1/transactions/**` | `transaction-service:8090` | `userOrIpKeyResolver` | 20 / 40 req/s |
| `transaction-service-interbank-route` | `/api/v1/interbank/**` | `transaction-service:8090` | `userOrIpKeyResolver` | 20 / 40 req/s |
| `orchestration-service-route` | `/api/v1/orchestration/**` | `orchestration-service:8091` | `userOrIpKeyResolver` | 20 / 40 req/s |
| `risk-engine-route` | `/internal/v1/risk/**` | `risk-engine:8092` (Internal) | `ipKeyResolver` | 30 / 60 req/s |
| `batch-service-route` | `/api/v1/batch/**` | `batch-service:8093` | `userOrIpKeyResolver` | 10 / 20 req/s |
| `mobile-bff-route` | `/api/v1/mobile/**` | `mobile-bff:8094` | `userOrIpKeyResolver` | 50 / 100 req/s |

All target URLs in `docker-compose.yml` are parameterized to allow running either from Docker or locally from an IDE with sensible localhost fallbacks.

---

## 6. Edge Rate Limiting Hardening (Redis Token-Bucket)

Rate limiting operates at the Gateway layer to protect downstream databases and services against credential brute force and denial of service:

### A. Algorithm & Parameters
- **Backend:** Redis-backed Token-Bucket algorithm via Spring Cloud Gateway's `RequestRateLimiter` filter.
- **Tiers Enforced:**
  1. **Authentication Endpoints (`/api/v1/auth/**`):** 10 req/s, Burst 20. Defends against credential brute-forcing.
  2. **Financial Mutation Endpoints (`/api/v1/ledger/**`, `/api/v1/transactions/**`, `/api/v1/orchestration/**`):** 20 req/s, Burst 40. Protects database row locks and Kafka commit threads.
  3. **Read / Operational Queries (`/api/v1/accounts/**`, `/api/v1/customers/**`, `/api/v1/notifications/**`):** 50 req/s, Burst 100.
  4. **Forensic Audit & Merkle Proofs (`/api/v1/audit/**`):** 30 req/s, Burst 60.

### B. Enterprise Key Resolvers ([RateLimiterConfig.java](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/backend/api-gateway/src/main/java/com/group2/fse/api_gateway/config/RateLimiterConfig.java))
1. **`ipKeyResolver`:** Resolves originating client IP address, properly inspecting `X-Forwarded-For` proxy headers before falling back to remote socket address. Used for public unauthenticated endpoints.
2. **`userOrIpKeyResolver`:** Extracts authenticated subject (`sub`) directly from the `Authorization: Bearer <JWT>` header. Falls back to IP resolution if no Bearer token is present. Used for business endpoints.

### C. Standard Headers & Error Response
Exceeded burst limits return **HTTP 429 Too Many Requests** with standard rate limiting headers:
- `X-RateLimit-Remaining`
- `X-RateLimit-Burst-Capacity`
- `X-RateLimit-Replenish-Rate`
- `X-RateLimit-Requested-Tokens`

### D. Automated Verification
- Unit test suite: [RateLimiterConfigTest.java](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/backend/api-gateway/src/test/java/com/group2/fse/api_gateway/config/RateLimiterConfigTest.java) (100% pass across all resolver modes).
- Integration test suite: [GatewayRoutingIntegrationTest.java](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/backend/api-gateway/src/test/java/com/group2/fse/api_gateway/route/GatewayRoutingIntegrationTest.java) (100% pass across all routes).
- Burst verification script: [scripts/test_rate_limiting.ps1](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/scripts/test_rate_limiting.ps1).

---

## 7. Dynamic CORS Configuration (Handshake H14)

Resolved in coordination with the frontend/mobile engineers:
- **Origins Allowed:** `http://localhost:3000`, `http://localhost:5173`, `http://127.0.0.1:3000`, `http://127.0.0.1:5173`, and emulator targets.
- **Methods Allowed:** `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`.
- **Headers Supported:** `Authorization`, `Idempotency-Key`, `X-Correlation-ID`, `Content-Type`.
- **Exposed Headers:** All rate limit metadata headers and `X-Correlation-ID` are explicitly exposed in CORS responses so client apps can monitor token bucket limits.
- **Mobile Native Note:** Native Flutter mobile HTTP clients bypass browser CORS preflight entirely; CORS configuration serves web portals and emulator web engines.

---

## 8. Test Automation & Script Modernization

The test automation harness has been upgraded across the repository:

1. **Seeded Persona Realignment:** Removed deprecated `/api/v1/auth/customers/register` self-registration flows and unseeded fallbacks. All scripts operate on frozen contract seed identities:
   - Primary Customer: `john_doe` / `Password123!` (Account `ACC_10000001`, initial ₱50,000.00).
   - Secondary Customer: `maria_santos` / `Password123!` (Account `ACC_10000002`, initial ₱25,000.00).
   - Seed Persona: `david_kim` / `Password123!` (Account `ACC_10000003`, initial ₱100,000.00).
   - Teller / Staff: `teller_alice` / `Password123!`.
   - Administrator: `admin` / `Password123!`.
2. **Correlation ID Tracing:** Injects `X-Correlation-ID: <UUID>` on all client requests across all scripts.
3. **Automated Test Scripts:**
   - [scripts/demo_golden_path.ps1](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/scripts/demo_golden_path.ps1): 7-tier E2E demonstration with formatted output table.
   - `scripts/test_api_endpoints.ps1`: Gateway route table validation.
   - `scripts/test_edge_cases_and_compliance.ps1`: RFC-7807 401/403/422 compliance verification.
   - `scripts/test_kafka_events.ps1`: Event streaming on KRaft topic `ledger.mutation.completed.v1`.
   - `scripts/test_user_lifecycle_flow.ps1`: Customer lifecycle and credential validation.
   - [scripts/test_rate_limiting.ps1](file:///c:/Users/JAC83768/Downloads/FSDay32/Group2_FSE_Capstone/scripts/test_rate_limiting.ps1): Rate limit headers, burst 429s, and perimeter 403 checks.

---

## 9. Team Handover Notes

### For Hans (Dev 2 - Identity & Account):
- `auth-service` and `account-service` run cleanly with HikariCP max pool 20 and connection timeout 5000ms.
- Auth routes are rate-limited to 10 req/s (burst 20) per IP.
- Account routes are rate-limited to 50 req/s (burst 100) per authenticated user.

### For Wayne (Dev 3 - Transactions, Ledger & Notifications):
- `transaction-service` (Port 8090) and `ledger-service` (Port 8083) routes are live on Gateway.
- Mutation routes (`/api/v1/ledger/**`, `/api/v1/transactions/**`) are rate-limited to 20 req/s (burst 40) per user to guard database locks.
- Requires `Authorization: Bearer <token>` and `Idempotency-Key: <UUID>` on mutations.

### For Ysa (Dev 4 - Risk Engine, Core Mock & Reversals):
- External public ingress to `/internal/v1/risk/**` is strictly blocked at the Gateway with **HTTP 403 Forbidden**.
- Downstream microservices communicate directly via internal bridge: `http://risk-engine:8092/internal/v1/risk/evaluate`.
- `orchestration-service` route (`/api/v1/orchestration/**` -> `:8091`) is live on Gateway.

### For April (Dev 5 - Database, Batch & Audit):
- `batch-service` route (`/api/v1/batch/**` -> `:8093`) and `audit-service` route (`/api/v1/audit/**` -> `:8085`) are live on Gateway.
- Audit hash chain verification (`GET /api/v1/audit/verify-chain/{accountId}`) verified through the Gateway.
- All database volumes (`oracle_data`, `postgres_data`, `redis_data`, `kafka_data`) survive container teardowns.

### For Neo & Gabo (Dev 6 & 7 - Flutter Foundation & Features):
- **Gateway Base URL:** `http://localhost:8080` (or Android Emulator: `http://10.0.2.2:8080`).
- **CORS Configured Origins:** `http://localhost:3000`, `http://localhost:5173`, `http://127.0.0.1:3000`, `http://127.0.0.1:5173`.
- **Response Headers:** Check `X-RateLimit-Remaining` to monitor remaining token capacity.

---

## 10. Verification Runbook (PowerShell)

To verify the entire perimeter on any environment:

```powershell
# 1. Navigate to repository root
Push-Location Group2_FSE_Capstone

# 2. Check running container status and port bindings
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 3. Test Perimeter Authentication (Unauthenticated -> 401 Unauthorized)
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/accounts/my-accounts" -Method Get -SkipHttpErrorCheck

# 4. Test Perimeter Rejection (Internal Route Blocked -> 403 Forbidden)
Invoke-WebRequest -Uri "http://localhost:8080/internal/v1/risk/evaluate" -Method Get -SkipHttpErrorCheck

# 5. Run Edge Rate Limiting & Perimeter Verification
powershell -ExecutionPolicy Bypass -File .\scripts\test_rate_limiting.ps1

# 6. Run API Gateway Maven Test Suite
mvn test -f backend/api-gateway/pom.xml

Pop-Location
```
