# DevOps, Cloud & Perimeter Infrastructure — Implementation & Handover Status

**Author:** Jared (Dev 8 — DevOps & Cloud Engineer)  
**Target Audience:** CooBS Engineering Team (Carl, Hans, Wayne, Ysa, April, Neo, Gabo)  
**Status:** Implemented & Verified in Local Running Stack  
**Primary Repository Path:** `Group2_FSE_Capstone/`  

---

## Executive Summary

This document summarizes the cloud architecture, database reliability, container networking, and perimeter security implementations deployed on the DevOps track. 

All infrastructure components—including the polyglot data tier (Oracle Free, PostgreSQL, Redis, Kafka KRaft), edge routing, HikariCP connection pool hardening, loopback network perimeter isolation, and the modernized End-to-End Golden Path test harness—are live, tested, and operating with 100% green health checks.

---

## 1. Cloud Architecture & Sizing Deliverables

### A. Event Streaming Head: Azure Event Hubs (Kafka Surface)
- **Architectural Decision:** Standard Azure Event Hubs with Kafka API compatibility selected as our cloud streaming baseline.
- **Key Trade-offs Evaluated:**
  * Zero cluster/broker maintenance overhead compared to self-hosted Strimzi Kafka on AKS.
  * Direct compatibility with existing Spring Cloud Stream and Kafka clients (`kafka-clients:3.x`) using connection string authentication over SASL/PLAIN.
  * Preserves full local development parity: local stack runs lightweight Apache Kafka in KRaft mode, while Azure uses Event Hubs without any code changes needed in microservices.

### B. Azure Cloud Sizing & Cost Model
- **Itemized Sizing Model Documented:** Complete architecture and pricing breakdown saved in `docs/CLOUD_DECISIONS_AND_AZURE_COST_MODEL.md`.
- **Target Cost Envelope:**
  * **1-Week Capstone Evaluation Window:** **$95.43** total spend with automated shutdown of compute nodes outside defense/testing hours.
  * **Full Month Production Baseline:** **$428.02/month** (AKS 3x `Standard_D4s_v5`, Azure Database for PostgreSQL Flexible Server, Azure Cache for Redis C1, Azure Bastion, and Container Registry).

---

## 2. Multi-Tier Database Health & Volume Isolation

To prevent container startup race conditions and database socket starvation during local runs and evaluation tests:

### A. Healthcheck Specifications Audit
| Container Name | Service Role | Healthcheck Mechanism | Interval / Timeout | Retries |
| :--- | :--- | :--- | :---: | :---: |
| `oracle-core-db` | Master System of Record (SoR) | `/opt/oracle/healthcheck.sh` (`SELECT 1 FROM dual;`) | 20s / 15s | 20 (60s start period) |
| `postgres-audit-db` | Immutable Forensic Audit Store | `pg_isready -U postgres -d audit_store` | 5s / 5s | 10 |
| `redis-cache` | Distributed Idempotency & Balance Cache | `redis-cli ping` | 5s / 3s | 5 |
| `kafka-broker` | Single Broker KRaft Event Streaming | `/opt/kafka/bin/kafka-broker-api-versions.sh` | 10s / 5s | 15 (15s start period) |

### B. Named Volume Persistence
All persistent stores use dedicated Docker volumes to ensure transactional ledgers, audit hash chains, and idempotency locks survive container stop and teardown cycles (`docker compose down` without `-v`):
- `oracle_data` $\rightarrow$ `/opt/oracle/oradata`
- `postgres_data` $\rightarrow$ `/var/lib/postgresql/data`
- `redis_data` $\rightarrow$ `/data`
- `kafka_data` $\rightarrow$ `/var/lib/kafka/data`

---

## 3. High-Concurrency Connection Pool Hardening (HikariCP)

Under heavy concurrency (e.g., JMeter benchmark testing), unconstrained connection pools cause database connection exhaustion, slow query thread stalls, and HTTP 500 errors. 

Standardized HikariCP environment parameters have been configured in `docker-compose.yml` across all 5 database-connected microservices (`ledger-service`, `auth-service`, `account-service`, `notification-service`, `audit-service`):

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
   │  - Correlation ID Injection (X-Correlation-ID)              │
   └──────────────────────────┬──────────────────────────────────┘
                              │
           Docker Bridge Network (`core-banking-net`)
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
 [ auth-service ]      [ ledger-service ]    [ audit-service ]
(127.0.0.1:8081)      (127.0.0.1:8083)      (127.0.0.1:8085)
```

### A. Host Port Binding Hardening
In `docker-compose.yml`, all backend microservice and database ports have been bound to `127.0.0.1` (loopback only) instead of `0.0.0.0`. 
- **Only Port 8080 (`api-gateway`)** is published on all network interfaces (`0.0.0.0:8080`).
- Devices on the local physical network cannot bypass the Gateway to call microservices directly on `8081`, `8082`, `8083`, etc.
- Developers can still access individual services on `localhost` for isolated debugging.

### B. Internal Route Blocker (`InternalPerimeterRejectionFilter.java`)
Implemented a high-precedence WebFlux filter on the API Gateway (`backend/api-gateway/src/main/java/com/group2/fse/api_gateway/filter/InternalPerimeterRejectionFilter.java`):
- Any request reaching the Gateway that targets internal-only routes (e.g., `/internal/**`, such as Ysa's `/internal/v1/risk/evaluate`) is immediately intercepted and rejected with **HTTP 403 Forbidden**.
- Rejections produce a standardized RFC-7807 Problem Detail response with `X-Correlation-ID` tracking.
- Verified with unit/integration tests in `GatewayRoutingIntegrationTest.java`.

### C. Perimeter Authentication Rejection
Calling protected core banking endpoints through the Gateway without a valid JWT token (e.g. `GET /api/v1/accounts/my-accounts`) returns **HTTP 401 Unauthorized**.

---

## 5. Modernized Golden Path Test Harness (`scripts/demo_golden_path.ps1`)

The repository demonstration script `scripts/demo_golden_path.ps1` has been overhauled to align with the team's frozen contract specifications:
1. **Eliminated Deprecated Registration:** Removed calls to `/api/v1/auth/customers/register` and removed the unseeded `customer1` fallback.
2. **Valid Seed Credentials:** Uses verified seeded customer identities:
   - Primary Customer: `john_doe` / `Password123!` (Account: `ACC_10000001`, initial balance ₱50,000.00).
   - Secondary Customer: `maria_santos` / `Password123!` (Account: `ACC_10000002`, initial balance ₱25,000.00).
   - Teller / Staff: `teller_alice` / `Password123!`.
   - Administrator: `admin` / `Password123!`.
3. **Correlation ID Tracking:** Injects `X-Correlation-ID: <UUID>` on all requests.
4. **Verified Double-Entry & Idempotency:** Validates sub-second transfer execution, atomic ledger mutations, Redis pre-flight mutex caching (`Idempotency-Key`), in-app notification publishing, and cryptographic audit hash chaining.

---

## 6. Team Integration & Handover Notes

### For Hans (Dev 2 - Identity & Account):
- `auth-service` and `account-service` are fully healthy in the container stack.
- When repointing `auth-service` to `identity_db`, let Jared know so connection strings and HikariCP settings can be validated.
- Seed credentials for testing: `john_doe` / `Password123!`, `maria_santos` / `Password123!`, `david_kim` / `Password123!`.

### For Wayne (Dev 3 - Transactions, Ledger & Notifications):
- `ledger-service` and `notification-service` are running with HikariCP max pool size 20 and leak detection threshold 15,000ms.
- All transfers routed through `POST /api/v1/ledger/transfers` via Gateway must provide `Authorization: Bearer <token>` and `Idempotency-Key: <UUID>`.
- Notifications consume and publish successfully to `notification-service`.

### For Ysa (Dev 4 - Risk Engine, Core Mock & Reversals):
- Internal risk route (`/internal/v1/risk/evaluate`) is strictly blocked from external public gateway traffic (returns **HTTP 403 Forbidden**).
- Downstream services inside Docker talk to `risk-engine` directly via the internal bridge `http://risk-engine:8092/internal/v1/risk/evaluate` without going through the public gateway.

### For April (Dev 5 - Database, Batch & Audit):
- Oracle, Postgres, and Redis named volumes are persistent across restarts.
- Audit chain verification (`GET /api/v1/audit/verify-chain/{accountId}`) verified green through the API Gateway.

### For Neo & Gabo (Dev 6 & 7 - Flutter Foundation & Features):
- **Gateway Base URL:** `http://localhost:8080` (or Android Emulator: `http://10.0.2.2:8080`).
- **CORS Configured Origins:** `http://localhost:3000`, `http://localhost:5173`, `http://127.0.0.1:3000`, `http://127.0.0.1:5173`. Native mobile HTTP calls bypass CORS preflight.
- **Standard Headers Supported:** `Authorization`, `Idempotency-Key`, `X-Correlation-ID`, `Content-Type`.

---

## 7. Verification Runbook (PowerShell)

To verify the stack on any machine:

```powershell
# 1. Navigate to repository root
Push-Location Group2_FSE_Capstone

# 2. Check running container status and port bindings
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 3. Test Perimeter Rejection (Unauthenticated -> 401 Unauthorized)
try {
    Invoke-WebRequest -Uri "http://localhost:8080/api/v1/accounts/my-accounts" -Method Get
} catch {
    Write-Host "Perimeter Check 1:" $_.Exception.Response.StatusCode.value__ "(" $_.Exception.Response.StatusDescription ")"
}

# 4. Test Perimeter Rejection (Internal Route Blocked -> 403 Forbidden)
try {
    Invoke-WebRequest -Uri "http://localhost:8080/internal/v1/risk/evaluate" -Method Get
} catch {
    Write-Host "Perimeter Check 2:" $_.Exception.Response.StatusCode.value__ "(" $_.Exception.Response.StatusDescription ")"
}

# 5. Run Full End-to-End Golden Path Demonstration
powershell -ExecutionPolicy Bypass -File .\scripts\demo_golden_path.ps1

Pop-Location
```
