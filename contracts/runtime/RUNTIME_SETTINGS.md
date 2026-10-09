# Runtime Settings (handshakes H12 and H13)

The names every service uses to find its database, Redis, Kafka and the other services. `docker-compose.yml` sets them; each owner reads them in the service's own `application.properties`. Use these names exactly. If a service needs one that is not here, it is added to Compose and to this page in the same change.

**Scope.** Local development on one Docker network. Splitting the cloud and on-premises zones into separate networks is deferred to a later phase; the names below do not change when that happens.

## 1. Ports and hosts

Inside Compose a service is reached by its container name. From the host, and when a service is run from an IDE, use `localhost` and the host port.

| Service | Container name | Port | Zone | Compose profile |
| --- | --- | --- | --- | --- |
| api-gateway | `api-gateway` | 8080 | Cloud | `app` |
| auth-service | `auth-service` | 8081 | Cloud | `app` |
| account-service | `account-service` | 8082 | On-premises | `app` |
| ledger-service | `ledger-service` | 8083 | On-premises | `app` |
| notification-service | `notification-service` | 8084 | Cloud | `app` |
| audit-service | `audit-service` | 8085 | On-premises | `app` |
| customer-service | `customer-service` | 8089 | Cloud | `expansion` |
| transaction-service | `transaction-service` | 8090 | Cloud | `expansion` |
| orchestration-service | `orchestration-service` | 8091 | Cloud | `expansion` |
| risk-engine | `risk-engine` | 8092 | Cloud | `expansion` |
| batch-service | `batch-service` | 8093 | On-premises | `expansion` |
| mobile-bff | `mobile-bff` | 8094 | Cloud | `expansion` |
| staff-bff | `staff-bff` | 8095 | Cloud | `expansion` |

| Data store | Container name | Port inside Compose | Host port | Compose profile |
| --- | --- | --- | --- | --- |
| Oracle (system of record) | `oracle-core-db` | 1521 | 1522 | none |
| PostgreSQL, on-premises (audit store, batch) | `postgres-audit-db` | 5432 | 5434 | none |
| PostgreSQL, cloud | `postgres-cloud-db` | 5432 | 5435 | `expansion` |
| Redis | `redis-cache` | 6379 | 6379 | none |
| Kafka | `kafka-broker` | 29092 | 9092 | none |

## 2. Service URLs (H13)

One variable per target service, holding the base URL with no trailing slash and no path. The caller appends the `/internal/v1/...` path from the API contract.

| Variable | Value in Compose |
| --- | --- |
| `AUTH_SERVICE_URL` | `http://auth-service:8081` |
| `ACCOUNT_SERVICE_URL` | `http://account-service:8082` |
| `LEDGER_SERVICE_URL` | `http://ledger-service:8083` |
| `NOTIFICATION_SERVICE_URL` | `http://notification-service:8084` |
| `AUDIT_SERVICE_URL` | `http://audit-service:8085` |
| `CUSTOMER_SERVICE_URL` | `http://customer-service:8089` |
| `TRANSACTION_SERVICE_URL` | `http://transaction-service:8090` |
| `ORCHESTRATION_SERVICE_URL` | `http://orchestration-service:8091` |
| `RISK_ENGINE_URL` | `http://risk-engine:8092` |
| `BATCH_SERVICE_URL` | `http://batch-service:8093` |
| `MOBILE_BFF_URL` | `http://mobile-bff:8094` |
| `STAFF_BFF_URL` | `http://staff-bff:8095` |

Which service receives which URL follows section 3.2 of the API contract, the list of allowed internal calls:

| Service | Receives |
| --- | --- |
| api-gateway | Every service with a public route: auth, account, ledger, notification, audit, customer, transaction, batch, mobile-bff, staff-bff |
| customer-service | `ACCOUNT_SERVICE_URL`, `TRANSACTION_SERVICE_URL` |
| transaction-service | `ACCOUNT_SERVICE_URL`, `ORCHESTRATION_SERVICE_URL` |
| orchestration-service | `CUSTOMER_SERVICE_URL`, `RISK_ENGINE_URL`, `LEDGER_SERVICE_URL`, `TRANSACTION_SERVICE_URL` (the inter-bank gateway, I4) |
| account-service | `LEDGER_SERVICE_URL` |
| batch-service | `LEDGER_SERVICE_URL` |
| mobile-bff | `CUSTOMER_SERVICE_URL`, `ACCOUNT_SERVICE_URL`, `TRANSACTION_SERVICE_URL`, `NOTIFICATION_SERVICE_URL` |
| staff-bff | `CUSTOMER_SERVICE_URL`, `ACCOUNT_SERVICE_URL`, `LEDGER_SERVICE_URL`, `TRANSACTION_SERVICE_URL`, `AUDIT_SERVICE_URL`, `BATCH_SERVICE_URL`, `NOTIFICATION_SERVICE_URL` |

The gateway has no route for any `/internal/**` path, so it receives no address for the risk engine.

The gateway bounds every call to a target. Both settings are optional:

| Variable | Default | Effect |
| --- | --- | --- |
| `GATEWAY_CONNECT_TIMEOUT_MS` | `3000` | A target that cannot be reached in this time (a service down, the on-premises link cut) is answered 503 |
| `GATEWAY_RESPONSE_TIMEOUT` | `15s` | A target that accepts the call but does not answer in this time is answered 504 |

In `application.properties`, give the local default so the service also runs from an IDE:

```properties
app.clients.ledger.base-url=${LEDGER_SERVICE_URL:http://localhost:8083}
```

Every client sets a timeout. The risk call's budget is 150 ms.

## 3. Databases (H12)

Schemas and data are disposable. Every table is created by the init scripts under `database/`, which run when a database volume is empty; a reset rebuilds everything from the seed. Services do not carry migrations and do not create or alter tables: set `spring.jpa.hibernate.ddl-auto=validate`.

### Cloud PostgreSQL

Init scripts: `database/postgres-cloud/`. One user for local development.

| Database | Schema | Service |
| --- | --- | --- |
| `identity_db` | `public` | auth-service |
| `customer_db` | `public` | customer-service |
| `transaction_db` | `txn` | transaction-service |
| `transaction_db` | `saga` | orchestration-service |
| `notification_db` | `public` | notification-service |

| Variable | Value in Compose | Default when run from an IDE |
| --- | --- | --- |
| `POSTGRES_CLOUD_HOST` | `postgres-cloud-db` | `localhost` |
| `POSTGRES_CLOUD_PORT` | `5432` | `5435` |
| `POSTGRES_CLOUD_DB` | The service's database from the table above | The same |
| `POSTGRES_CLOUD_SCHEMA` | `txn` or `saga`; not set for the `public` schema | The same |
| `POSTGRES_CLOUD_USER` | `postgres` | `postgres` |
| `POSTGRES_CLOUD_PASSWORD` | Taken from `POSTGRES_PASSWORD` in `.env` | No default: fail at startup if missing |

```properties
spring.datasource.url=jdbc:postgresql://${POSTGRES_CLOUD_HOST:localhost}:${POSTGRES_CLOUD_PORT:5435}/${POSTGRES_CLOUD_DB:transaction_db}
spring.datasource.username=${POSTGRES_CLOUD_USER:postgres}
spring.datasource.password=${POSTGRES_CLOUD_PASSWORD}
spring.jpa.properties.hibernate.default_schema=${POSTGRES_CLOUD_SCHEMA:saga}
spring.jpa.hibernate.ddl-auto=validate
```

`customer-service`, `transaction-service` and `orchestration-service` receive these today. `auth-service` and `notification-service` receive them when their owners move them onto `identity_db` and `notification_db`; until then they keep their current variables.

### Cloud databases on SQL Server (opt-in)

In Azure the four cloud databases are Azure SQL. `docker-compose.azure-sql.yml` runs the same thing locally: a SQL Server 2022 container (`sqlserver-cloud-db`) and a loader for `database/azure-sql/`. The databases, schemas and owning services are the ones in the table above. The override changes no `application.properties`; it supplies Spring's own variables, which take precedence over the PostgreSQL lines there. The five services carry the SQL Server JDBC driver beside the PostgreSQL one, so the same image runs on either.

| Variable | Value in the override | Note |
| --- | --- | --- |
| `CLOUD_DB_PASSWORD` | From `.env` | The `sa` password. No default: Compose stops if it is missing. The Kubernetes Secret uses the same name |
| `SQLSERVER_CLOUD_PORT` | `1433` | Host port, published on `127.0.0.1` only |
| `SPRING_DATASOURCE_URL` | `jdbc:sqlserver://sqlserver-cloud-db:1433;databaseName=<database>;encrypt=true;trustServerCertificate=true;loginTimeout=30` | `trustServerCertificate=true` is for the local container only; Azure SQL uses `false` |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` | |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | `sa`, `CLOUD_DB_PASSWORD` | |
| `SPRING_JPA_DATABASE_PLATFORM`, `SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT` | `org.hibernate.dialect.SQLServerDialect` | |
| `POSTGRES_CLOUD_SCHEMA` | Unchanged: `txn` or `saga` | SQL Server has the same schemas |

`notification-service` takes the same values through its own names: `DATABASE_URL`, `DATABASE_DRIVER`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`.

### On-premises

| Store | Variables | Used by |
| --- | --- | --- |
| Oracle | `ORACLE_HOST`, `ORACLE_PORT`, `ORACLE_DATABASE`, `APP_USER`, `APP_USER_PASSWORD` | ledger-service, account-service; auth-service until it moves |
| PostgreSQL `audit_store` database | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | ledger-service, audit-service, batch-service |
| Schema within it | `POSTGRES_SCHEMA` = `batch` | batch-service |

The `batch` schema exists only in the rebuilt schema under `database/next/postgres/`. Until that becomes the default, `batch-service` needs the stack started with `docker-compose.target.yml`.

No cloud service receives an Oracle or on-premises PostgreSQL variable. A cloud service reaches on-premises data only through `ACCOUNT_SERVICE_URL`, `LEDGER_SERVICE_URL`, `AUDIT_SERVICE_URL` or `BATCH_SERVICE_URL`.

## 4. Redis and Kafka

| Variable | Value in Compose | Default from an IDE | Received by |
| --- | --- | --- | --- |
| `REDIS_HOST` | `redis-cache` | `localhost` | gateway, auth, account, ledger, customer, transaction, risk |
| `REDIS_PORT` | `6379` | `6379` | The same |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-broker:29092` | `localhost:9092` | ledger, account, notification, transaction, orchestration |
| `KAFKA_CONSUMER_GROUP` | `<service-name>-group` | The same | notification, transaction, orchestration |

Topic names and their `.DLT` twins are in section 13 of the API contract and are created by the `kafka-init-topics` step in Compose.

Whether `batch-service` publishes its step event itself or through the ledger outbox is still open (decision 6 in the zero-conflict plan), so it receives no Kafka variable yet.

## 5. Security

| Variable | Rule |
| --- | --- |
| `JWT_SECRET` | The same value in every service. No fallback in any `application.properties`: a missing secret fails at startup |
| `INTERNAL_SERVICE_SECRET` | risk-engine only: the value it accepts in the `X-Internal-Secret` header. No fallback: a missing value fails at startup. Compose takes it from `.env` |
| Database passwords (`POSTGRES_CLOUD_PASSWORD`, `POSTGRES_PASSWORD`, `APP_USER_PASSWORD`, `DATABASE_PASSWORD`) | No fallback in any `application.properties`: a missing password fails at startup |
| `JWT_EXPIRATION_MS` | Default 86400000 |
| `CORS_ALLOWED_ORIGINS` | Default `http://localhost:3000,http://localhost:5173,http://localhost:8088,http://127.0.0.1:8088`. Fixed port `8088` handles Flutter web development (H14). Native Android (`10.0.2.2:8080`) uses direct OS sockets, bypassing browser SOP with zero CORS friction |

### Inter-bank partners (transaction-service)

| Variable | Rule |
| --- | --- |
| `INTERBANK_SHARED_SECRET`, `INTERBANK_BANKB_SECRET` | The secret shared with a partner bank: checks the signature on what it sends us (I1 to I3) and signs what we send it (I4) |
| `INTERBANK_BANKB_URL` | Optional. The address of the partner's inter-bank endpoints, for example `http://partner-host:8090`. Empty by default, which makes `BANKB` our own loopback: no network call is made |

## 6. Changing a setting

`docker-compose.yml` and `.env.example` are Dev 8 files. A new or changed variable is made there and on this page in one change, and announced in the H12 or H13 thread with every service that reads it.
