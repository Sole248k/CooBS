# 12. Deployment and operations

Two environments exist: the **local Docker Compose stack** (development, testing, demonstration fallback) and the **Azure deployment** (the demonstration environment). Both run the same images and the same configuration names.

## 12.1 Repository layout

| Path | Contents |
| --- | --- |
| `backend/` | 13 Spring Boot services and the Maven aggregator `pom.xml` |
| `frontend/` | React staff console, `Dockerfile`, `nginx.conf` |
| `app/` | Flutter member app |
| `database/` | Init scripts per store, seed generator, Azure SQL translation (section 8) |
| `contracts/` | Frozen API contract, OpenAPI, examples, runtime settings |
| `infra/` | Bicep, Kubernetes manifests, on-premises Compose, deploy scripts, Datadog |
| `monitoring/` | Prometheus, Grafana, Loki, Promtail configuration for the local stack |
| `postman/` | Postman collections, environment and the E2E suite |
| `scripts/` | Setup, start/stop/reset, demos, scenario tests, release checks |
| `test/` | JMeter plan and results |
| `docs/`, `integrated-capstone-plans/`, `tasks/` | Documentation and plans |
| `azure-pipelines.yml`, `.github/workflows/ci.yml` | CI |
| `docker-compose*.yml` | Local stacks |

## 12.2 Prerequisites

Docker Desktop with Compose v2 and at least 8 GB of memory for the full stack (Oracle alone needs ~2 GB); Java 21 and Maven (or the bundled `mvnw`) for service development; Node.js 20+ for the console and Newman; Flutter 3.44 for the app; PowerShell for the scripts (Windows PowerShell 5.1 or `pwsh`); JMeter for load tests.

## 12.3 Local stack

`docker-compose.yml` defines one network (`core-banking-net`) and these profiles:

| Profile | Containers |
| --- | --- |
| *(none)* | `oracle-core-db` (:1522), `postgres-audit-db` (:5434), `redis-cache` (:6379), `kafka-broker` (:9092), `kafka-init-topics`, `kafka-ui` (:8088), `swagger-ui` (:8086) |
| `app` | `api-gateway` (:8080), `auth-service`, `account-service`, `ledger-service`, `notification-service`, `audit-service`, `postgres-cloud-db` (:5435), `customer-service` |
| `expansion` | `transaction-service`, `orchestration-service`, `risk-engine`, `batch-service`, `mobile-bff`, `staff-bff`, `frontend` (:3000) (and `postgres-cloud-db`, `customer-service`) |
| `observability` | `prometheus` (:9090), `grafana` (:3001), `loki` (:3100), `promtail` |
| `tracing` | `jaeger` (:16686, OTLP 4317/4318) |
| `full` | observability + tracing |

Service ports are bound to `127.0.0.1` except the gateway and the console.

**Start everything:**

```bash
cp .env.example .env                                   # first time; .env is also committed with dev values
docker compose --profile app --profile expansion up -d --build
docker compose --profile app --profile expansion --profile observability up -d   # with monitoring
docker compose ps
```

Data tier only (run services from the IDE): `docker compose up -d` or `scripts/start.ps1`. Stop: `scripts/stop.ps1`. Wipe and reseed: `scripts/reset.ps1` (`docker compose down -v`). First-time checks: `scripts/setup.ps1` / `setup.sh`; health check: `scripts/verify.ps1`.

Each Java container runs with `-Xmx256m -XX:+ExitOnOutOfMemoryError`; containers depend on their databases' health checks, so Oracle's first start (several minutes) gates `ledger-service` and `account-service`.

**Alternative stacks:**
- `docker-compose.azure-sql.yml` — the four cloud databases on SQL Server 2022, as in Azure.
- `docker-compose.target.yml` — mounts `database/next/` (target on-premises schema) on separate volumes.

**Local URLs**

| What | URL |
| --- | --- |
| Staff console | http://localhost:3000 |
| Gateway / API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui or http://localhost:8086 |
| Kafka UI | http://localhost:8088 |
| Grafana | http://localhost:3001 |
| Prometheus | http://localhost:9090 |
| Jaeger | http://localhost:16686 |

**Direct access**

```bash
docker exec -it oracle-core-db bash -c 'sqlplus "${APP_USER}/${APP_USER_PASSWORD}@localhost:1521/${ORACLE_DATABASE}"'
docker exec -it postgres-audit-db psql -U postgres -d audit_store
docker exec -it postgres-cloud-db psql -U postgres -d transaction_db
docker exec -it redis-cache redis-cli
docker exec -it kafka-broker /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic transaction.stage.changed.v1 --from-beginning
docker compose logs -f orchestration-service
```

## 12.4 Configuration

Every setting is an environment variable. The authoritative list with defaults is `.env.example` and `contracts/runtime/RUNTIME_SETTINGS.md`. The most important:

| Variable | Used by | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | All services | Signs and checks user and service tokens (no default) |
| `JWT_EXPIRATION_MS` | All | Access token life (86 400 000 locally) |
| `INTERNAL_SERVICE_SECRET` | risk-engine | Alternative internal auth header |
| `CORS_ALLOWED_ORIGINS` | Gateway and services | Browser origins |
| `ORACLE_HOST/PORT/DATABASE`, `APP_USER`, `APP_USER_PASSWORD`, `ORACLE_PASSWORD` | account, ledger, batch | System of record |
| `POSTGRES_HOST/PORT/DB/USER/PASSWORD` | ledger, audit, batch | Audit store |
| `POSTGRES_CLOUD_HOST/PORT/DB/USER/PASSWORD`, `CLOUD_DB_PASSWORD` | auth, customer, transaction, orchestration | Cloud databases |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `DATABASE_DRIVER` | notification | `notification_db` (JDBC URL form so Azure SQL can be used) |
| `REDIS_HOST`, `REDIS_PORT` | gateway, auth, account, ledger, customer, transaction, risk | Redis |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka users | Broker address |
| `*_SERVICE_URL`, `RISK_ENGINE_URL`, `MOBILE_BFF_URL`, `STAFF_BFF_URL` | Gateway, BFFs, callers | Where each service lives |
| `GATEWAY_CONNECT_TIMEOUT_MS`, `GATEWAY_RESPONSE_TIMEOUT` | Gateway | 3000 ms / 15 s |
| `CLIENT_CONNECT_TIMEOUT_MS`, `CLIENT_READ_TIMEOUT_MS` | BFFs | Downstream time limits |
| `INTERBANK_BANKB_SECRET`, `INTERBANK_BANKB_URL`, `INTERBANK_AURABANK_API_KEY`, `INTERBANK_AURABANK_URL` | transaction | Partner banks (empty URL = loopback) |
| `BATCH_KAFKA_ENABLED`, `BATCH_STEP_COMPLETED_TOPIC` | batch | EOD events |
| `SSL_ENABLED`, `SSL_KEY_STORE*` | Gateway | Optional TLS at the gateway |

Saga tuning (properties in `orchestration-service`): `app.saga.capture-attempts=3`, `capture-backoff=500ms`, `hold-retry-window=5m`, `sweep-interval=30s`, `sweep-stale-after=60s`, client timeouts (customer 500 ms, risk 150 ms, ledger 2 s, inter-bank 30 s), `app.warm-up.keep-warm-interval=20s`.

## 12.5 Building

```bash
cd backend && mvn clean verify                  # all services through the aggregator pom (needs Maven)
cd backend/ledger-service && ./mvnw clean test  # one service with its own wrapper (Windows: mvnw.cmd)
docker compose build ledger-service             # one image
```

Each service's `Dockerfile` is multi-stage: `maven:3.9-eclipse-temurin-21-alpine` builds, `eclipse-temurin:21-jre-alpine` runs as a non-root user.

## 12.6 Azure deployment

### Topology

| Component | Resource |
| --- | --- |
| Resource group | `CooBS-rsg`, Indonesia Central, availability zone 3 (Azure free trial) |
| Public entry | `48.193.9.148`, DNS `coobs.indonesiacentral.cloudapp.azure.com`, ports 80/443 |
| Cloud zone | AKS `aks-coobs-dev` (Free tier, Kubernetes 1.35, one `Standard_E2as_v5` node: 2 vCPU/16 GB), namespace `coobs`: 9 services, `frontend`, `tls-proxy` (Caddy 2.10), Kafka StatefulSet |
| On-premises zone | VM `vm-coobs-onprem` (`Standard_B2as_v2`, 2 vCPU/8 GB, Ubuntu 24.04, `10.250.0.4`, no public IP), Compose project `coobs-onprem`: account, ledger, audit, batch, Oracle, audit PostgreSQL, Datadog agent |
| Networks | `vnet-coobs-cloud-dev` 10.240.0.0/16 (`snet-aks`, `snet-private-endpoints`); `vnet-coobs-onprem-dev` 10.250.0.0/16 (`snet-core`); peered; NSG `nsg-coobs-onprem`; NAT gateway for the VM |
| Data | Azure SQL `sql-coobs-c2whi3` (4 × S0); Azure Managed Redis `redis-coobs-c2whi3` (Balanced_B0, TLS, private endpoint) |
| Secrets | Key Vault `kv-coobs-c2whi3` (13 secrets) |
| Images | ACR `CooBS`, 14 repositories under `coobs/` |
| Logs | Log Analytics `law-coobs-dev` (Container Insights, 1 GB/day cap) |

Cloud services reach the on-premises services by name: four selector-less Kubernetes Services with EndpointSlices pointing at `10.250.0.4`, so `http://ledger-service:8083` works exactly as locally. The VM reaches Kafka at `10.240.15.200:9094` (internal load balancer).

Resource sizing: each Java Deployment has 1 replica, 384 MB request / 640 MB limit, 50m CPU request, 256 MB heap; probes on `/actuator/health` with a 6-minute start-up allowance; non-root, capabilities dropped. The node can reserve 1900m CPU and has about 80m spare.

### Planned versus deployed

| Planned | Deployed | Reason |
| --- | --- | --- |
| Azure Front Door (TLS, WAF) | Caddy pod with Let's Encrypt | Front Door forbidden on a free trial |
| VPN Gateway to a datacenter | VNet peering + NSG; VM as datacenter | Cost and creation time; allowed for a lab |
| Azure Database for PostgreSQL | Azure SQL (T-SQL scripts generated) | Project decision; audit store stays PostgreSQL for its triggers |
| Event Hubs Standard | Kafka in the cluster | Event Hubs Standard allows 10 hubs; the system uses 20 topics |
| Azure Cache for Redis | Azure Managed Redis B0 | The former can no longer be created |
| 3 nodes, 12 vCPU | 1 node, 2 vCPU | Trial quota of 4 vCPU in the region |
| 7 cloud services | 9 (two BFFs added) | One BFF per client |
| Flutter web + Android | Android only | Web target not taken forward |

### Procedure (from nothing)

| Step | Command |
| --- | --- |
| 1 | `az provider register --namespace Microsoft.OperationsManagement --wait` (once per subscription) |
| 2 | Set `COOBS_SQL_ADMIN_PASSWORD`, `COOBS_VM_SSH_PUBLIC_KEY`, `COOBS_KEYVAULT_ADMIN_OBJECT_ID` in the shell |
| 3 | `az deployment group create --resource-group CooBS-rsg --name coobs-demo --parameters infra/bicep/main.bicepparam` (Bicep modules: network, monitoring, keyvault, sql, redis, aks, onprem-vm, frontdoor [off]) |
| 4 | `az acr login --name CooBS` then `infra/scripts/publish-images.ps1 -ImageTag <tag>` (14 images, linux/amd64) |
| 5 | `infra/scripts/deploy-cloud.ps1 -ImageTag <tag> -DnsLabel coobs -AcmeEmail <address>` — generates secrets into Key Vault, creates the Kubernetes Secret, loads Azure SQL with a Job inside the cluster, renders and applies the kustomize manifests, waits for every rollout |
| 6 | `infra/scripts/deploy-onprem.ps1 -KeyVaultName kv-coobs-c2whi3 -ImageTag <tag> -RedisHost <redis host>` — packs Compose, `start.sh` and init scripts into `az vm run-command`; the VM reads its secrets with its managed identity |
| 7 | `npx newman run postman/e2e/CooBS_End_To_End.postman_collection.json -e postman/CooBS_Local.postman_environment.json --env-var baseUrl=https://coobs.indonesiacentral.cloudapp.azure.com --env-var staffPassword=<from Key Vault> --env-var tellerPassword=<same> --delay-request 150` |
| 8 | Optional monitoring: `deploy-cloud.ps1 … -TraceServices <list>` then `infra/scripts/setup-datadog.ps1 -KeyVaultName <vault>` |
| 9 | App: `flutter build apk --release --dart-define=GATEWAY_URL=https://coobs.indonesiacentral.cloudapp.azure.com` |

Script safeguards: one tag for all images, checked in the registry before anything changes; HTTPS kept unless `-PlainHttp`; services restarted when the ConfigMap changes; each rollout awaited; monitoring never fails the deployment.

**Update:** `publish-images.ps1 -ImageTag <new>` → `deploy-cloud.ps1 -ImageTag <new> -SkipSchemaLoad` → `deploy-onprem.ps1 … -ImageTag <new>`. **Roll back:** the same with the earlier tag. **Reset data:** `-ResetData` on `deploy-onprem.ps1`, then on `deploy-cloud.ps1`. **Status:** `kubectl get pods -n coobs`, `kubectl logs deployment/<name> -n coobs --tail 100`, `deploy-onprem.ps1 -StatusOnly`. **Tear down:** delete the resource group (Key Vault is soft-deleted for 7 days). Full runbook: `infra/README.md`; as-built record and 15 resolved problems: `integrated-capstone-plans/AZURE_DEPLOYMENT_AS_BUILT.md`.

Cost: about USD 10/day (node, VM, four databases); trial credit USD 200, expiring 31 October 2026.

## 12.7 Observability

### Signals

| Signal | Local | Azure |
| --- | --- | --- |
| Metrics | Each service's `/actuator/prometheus` scraped every 5 s by Prometheus | Datadog agent scrapes the same endpoints (published as `coobs.*`); `mobile-bff` excluded (needs admin token) |
| Logs | Promtail reads container logs via the Docker socket → Loki (multiline Java stack traces, level labels) | Datadog agent on the node and on the VM; Container Insights to Log Analytics |
| Traces | Jaeger (OTLP), `tracing` profile | Datadog Java tracer 1.67 in the nine cluster services (init-container patch, no image rebuild); VM services not traced |
| Correlation | `X-Correlation-ID` on every request, log line (MDC) and event | Same, plus trace IDs injected into logs (`DD_LOGS_INJECTION`) |

Key metrics: `http_server_requests_seconds` (by `application`, `uri`, `method`, `status`), `spring_cloud_gateway_requests_seconds` (by `routeid`), `spring_kafka_listener_seconds`, `hikaricp_connections*`, `jvm_memory_used_bytes`, `jvm_threads_live_threads`, `jvm_gc_pause_seconds`, `process_cpu_usage`, `logback_events` (by `level`).

### Local dashboards and alerts

Grafana (http://localhost:3001, anonymous) provisions two dashboards from `monitoring/grafana/dashboards/`: **CooBS Core Banking — Operational Command Center** (`coobs_core_banking.json`: TPS, p99 latency, HikariCP pools, JVM) and **Centralized Log Stream & Error Inspector** (`coobs_centralized_logs.json`: Loki streams, log velocity, errors).

Prometheus alert rules (`monitoring/prometheus/alerts.yml`): `ServiceDown`, `HighHttp5xxRate`, `SlowTransferLatencyP99`, `HikariConnectionPoolSaturation`, `RiskEvaluationSlaBreachP95`, `GatewayEdgeIngressSlaBreachP99`, `TransferSagaLatencySlaBreachP95`, `HighRateLimitBurstSurge`, `KafkaConsumerLagHigh`. Runbook: `docs/docs-services/ENTERPRISE_OBSERVABILITY_GUIDE.md`.

### Azure (Datadog)

Created by `infra/scripts/setup-datadog.ps1`: log pipelines "CooBS services: severity" (Grok parses Spring Boot level) and "CooBS PostgreSQL: severity"; dashboard "CooBS services" (16 panels, `infra/k8s-monitoring/datadog-dashboard.json`); monitors for a host that stops reporting, container restarts, more than five 5xx in five minutes per service, and any error log line.

### Expected noise

- `CustomerNotFoundException … customer ID: 0` every 20 s — the orchestrator's keep-warm call (customer 0 does not exist by design).
- `TransactionNotFoundException … TX-20261003-8F3A21C0` — the console tracker's sample reference.
- `CancellationException: Request cancelled` — a call ended at its time limit.

## 12.8 Operational runbook

| Situation | What to check / do |
| --- | --- |
| Transfer stuck in `PROCESSING` | Tracker (staff view) shows the last stage. `GET /internal/v1/sagas/{id}` (from inside the network) shows state. `RISK_APPROVED`/`FUNDS_RESERVED` are retried by the sweep every 30 s; check `ledger-service` and the link to the VM. An outbound inter-bank transfer at `FUNDS_RESERVED` waits for the partner's answer |
| Many `RISK_UNAVAILABLE` | `risk-engine` up? Redis reachable? First call after idle should be warm (keep-warm every 20 s) |
| Notifications missing | `notification-service` logs; Kafka UI consumer lag; console DLQ screen → replay |
| Saga stays `POSTED` | `notification.dispatched.v1` not received; money has moved. Check notification-service and Kafka |
| EOD refuses to start (`BATCH_ALREADY_RUNNING`) | A run is `RUNNING`; a restart of `batch-service` marks a dead run `HALTED` |
| EOD halted at `GL_RECONCILIATION` | Debits ≠ credits for the day: inspect the journal for a one-legged posting |
| Audit chain broken | `GET /api/v1/audit/verify-chain/{accountId}` reports where; the table cannot be updated, so a break means tampering below the application or a restore |
| Console sign-in refused in Azure, API works | Gateway holds an old CORS origin list; restart the gateway (deploy script does this when the ConfigMap changes) |
| Pods `Pending` in Azure | CPU reservations on the single node; do not enable heavy add-ons (managed Prometheus evicted six services on 11 October) |
| Reset after a demo | Local `scripts/reset.ps1` or `scripts/reset_demo_data.ps1`; Azure `-ResetData` on both deploy scripts |
