# 10. Security

## 10.1 Overview

| Layer | Control |
| --- | --- |
| Transport (public) | TLS 1.3, Let's Encrypt certificate at the Caddy proxy; HTTP → HTTPS 308; HSTS (Azure). Plain HTTP locally |
| Edge | One gateway; per-route Redis rate limits; `/internal/**` refused at nginx and at the gateway; CORS judged once at the gateway |
| Identity | Password sign-in (bcrypt), JWT access tokens, rotating refresh tokens, revocation list, single session for members |
| Authorisation | Each service validates the token itself and applies role checks (`@PreAuthorize`, `SecurityConfig`) and ownership checks |
| Service identity | `ROLE_SERVICE` tokens for `/internal/v1/**` |
| Partner banks | HMAC-SHA256 request signatures or API key, timestamp window |
| Data integrity | Double-entry with DB constraints, idempotency everywhere, append-only hash-chained audit, daily seal |
| Network (Azure) | Two VNets with an NSG between; databases have no public endpoint; private endpoint for Redis |
| Secrets (Azure) | Key Vault; copied to a Kubernetes Secret at deploy; read by the VM's managed identity |
| Containers | Non-root users, all capabilities dropped, seccomp `RuntimeDefault`, no service-account token mounted |

## 10.2 Authentication

**Sign-in** (`auth-service`): username and password against `identity_db.app_user`, bcrypt cost 10. Users whose `status` is not `ACTIVE` are refused (seed: `lourdes_salazar`). Members use `/customers/login`, staff use `/staff/login`; the two populations are separate.

**Access token**: JWT, HMAC-SHA signed with `JWT_SECRET` (shared by every service so each can validate locally). Claims:

```json
{ "sub": "john_doe", "jti": "378c0fb9-…", "userId": 1, "roles": ["ROLE_CUSTOMER"], "userType": "CUSTOMER" }
```

`userId` is the customer ID for members and the staff user ID for staff. Lifetime `JWT_EXPIRATION_MS`: 24 h in local Compose, 2 h in Azure (auth-service's own default is 15 min).

**Refresh token**: opaque, stored in Redis (`refresh:{token}`) for 7 days, rotated on every refresh; refused if the user is no longer `ACTIVE`. The mobile app's fingerprint sign-in uses the stored refresh token.

**Single session (members)**: `SingleSessionGuard` keeps the member's current pair in Redis (`session:customer:{id}`). A new sign-in deletes the old refresh token and blacklists the old access token, so the earlier device is refused on its next call. Staff may have several sessions.

**Revocation**: `POST /api/v1/auth/token/revoke` writes `blacklist:jti:{jti}` with the token's remaining lifetime. Checked by `auth-service`, `account-service` and `ledger-service`; other services rely on expiry.

**Not implemented**: MFA (always `mfaRequired: false`; the `.env` MFA/SMS/SMTP settings are unused placeholders), registration, password reset, account lockout after failed attempts.

## 10.3 Roles and authorisation

| Role | Holder |
| --- | --- |
| `ROLE_CUSTOMER` | Members |
| `ROLE_TELLER` | Tellers |
| `ROLE_ADMIN` | Administrators and branch managers |
| `ROLE_SERVICE` | Other services (internal calls only) |
| `AUDITOR` | Accepted by two audit endpoints; no user is issued it |

### Staff console permission matrix

| Capability | Teller | Admin |
| --- | --- | --- |
| Customer 360 | Yes | Yes |
| Counter deposit and withdrawal | Yes | Yes |
| Assisted transfer (send on a member's behalf) | **Yes** | **No** |
| KYC queue: approve / reject | Yes | Yes |
| Closure queue | View only | Approve / reject |
| Open account, freeze, activate, add flag | Yes | Yes |
| Remove flag | No | **Yes** |
| Transaction tracker and reversal | Yes | Yes |
| Audit statement, chain verification, seal | Yes | Yes |
| End of day: view runs | Yes | Yes |
| End of day: trigger | No | **Yes** |
| Dead-letter queue | **Hidden** | View and replay |
| Ledger direct endpoints | Yes | Yes |

### Ownership checks

- A member reads only their own profile, 360, accounts, closure requests, vaults, share capital, transactions and notifications.
- T1 refuses a member sending from an account they do not own (`ACCOUNT_NOT_OWNED`); staff may send from any account (assisted transfer).
- Timeline: the staff view (risk score and decline reasons) is chosen by role, never by the `view` query parameter.
- Customer 360 shows `riskScore` only to staff.
- Members cannot call `/api/v1/ledger/**`, `/api/v1/audit/**` statements, or `/api/v1/batch/**`.

## 10.4 Service-to-service identity

Each calling service mints a short-lived JWT (5 minutes, cached until expiry) signed with the shared `JWT_SECRET`:

```json
{ "sub": "orchestration-service", "userId": 0, "roles": ["ROLE_SERVICE"], "userType": "SERVICE" }
```

Every `/internal/v1/**` endpoint requires `ROLE_SERVICE` and nothing else; a user token is refused with 403. The human who caused the action travels in the body as `actorId`/`actorType`. `risk-engine` additionally accepts the header `X-Internal-Secret` (or `X-API-Key`) equal to `INTERNAL_SERVICE_SECRET`.

The gateway has no route to internal paths and refuses any `/internal/**` request including URL-encoded variants (`InternalPerimeterRejectionFilter`); the console's nginx returns 403 for `/internal` before the gateway.

## 10.5 Abuse and integrity controls

| Threat | Control |
| --- | --- |
| Credential stuffing | 10 req/s per IP on `/api/v1/auth/**` (burst 20) |
| Request floods | Per-route token buckets keyed on user (or IP) |
| Double submission / replay | `Idempotency-Key` with Redis lock + DB unique keys; replays return the first answer; same key with a different body → 409 |
| Race on balances | Ordered `SELECT … FOR UPDATE`; DB `CHECK` constraints |
| Overdraft | `available_balance >= 0` constraint and application check |
| Second reversal | Unique `reverses_transaction_id`; `ALREADY_REVERSED` |
| Fraud | Risk engine (section 6), fail-closed |
| Tampering with history | Append-only audit table; SHA-256 chain; UPDATE/DELETE trigger; daily seal; chain verification API |
| Cross-member data access | Ownership checks in each service |
| Log forgery of signatures | Expected HMAC signatures are never logged |

## 10.6 Inter-bank authentication

Inbound calls to `/api/v1/interbank/**` carry `X-Bank-Code` and either:

- **Signature profile (preferred):** `X-Timestamp` (ISO-8601, within ±300 s) and `X-Signature = hex(HMAC-SHA256(sharedSecret, "<X-Timestamp>.<rawBody>"))`, compared in constant time; or
- **API-key profile:** `X-Api-Key` equal to the partner's shared secret (timestamp optional). If `X-Bank-Code` is missing, the bank is identified by the key.

Unknown bank → `UNKNOWN_BANK`; stale timestamp → `TIMESTAMP_EXPIRED`; mismatch → `INVALID_SIGNATURE`. Outbound calls to proposal-style partners are signed the same way with our bank code `COOBS`. Partner secrets come from `INTERBANK_BANKB_SECRET` and `INTERBANK_AURABANK_API_KEY`.

## 10.7 Secrets and configuration

| Secret | Local source | Azure source |
| --- | --- | --- |
| `JWT_SECRET` | `.env` | Key Vault `jwt-secret` (generated) |
| `INTERNAL_SERVICE_SECRET` | `.env` | `internal-service-secret` (generated) |
| `INTERBANK_BANKB_SECRET`, inter-bank shared secret | `.env` | `interbank-bankb-secret`, `interbank-shared-secret` (generated) |
| Oracle `ORACLE_PASSWORD`, `APP_USER_PASSWORD` | `.env` | `oracle-password`, `app-user-password` (generated) |
| `POSTGRES_PASSWORD` (audit) | `.env` | `audit-db-password` (generated) |
| Cloud DB password | `.env` `CLOUD_DB_PASSWORD` | `cloud-db-password` (entered once) |
| Redis key | none locally | `redis-key` (from the Redis resource) |
| Staff password | Seeded `Password123!` | `staff-password` (generated; its bcrypt hash replaces the seed at load) |
| Datadog keys | — | `datadog-api-key`, `datadog-site`, `datadog-app-key` |

No `application.properties` gives the core secrets (JWT, database passwords) a default, so a service without them does not start. Exceptions with defaults in code or committed files are listed in [section 14](14-known-gaps-and-limitations.md#145-security-findings-code). `.env` is committed with development values; `.env.example` documents every variable.

## 10.8 Network security (Azure)

- Public: one IP, ports 80/443 only, to the Caddy pod.
- NSG on the on-premises subnet: `Allow-Cluster-To-Core-Services` (priority 100) admits `snet-aks` to TCP 8082, 8083, 8085, 8093; `Deny-Everything-Else-From-Cloud` (priority 4000). Oracle and PostgreSQL publish no port off the VM.
- Azure SQL: no public access; VNet rule for `snet-aks` only. Encryption with certificate validation.
- Redis: TLS on 10000 via private endpoint; private DNS zone linked to both VNets.
- VM: no public IP; outbound through a NAT gateway; administered with `az vm run-command`.
- Inside the cluster and across the link: plain HTTP on private addresses (no mTLS); Kafka PLAINTEXT.

## 10.9 Client-side security

- **Mobile app:** tokens in `flutter_secure_storage` (Android Keystore/iOS Keychain, memory fallback); biometric unlock via `local_auth`; inactivity watcher signs the member out after 15 minutes; automatic token refresh on 401; payment QR codes contain only account number and optional amount, as plain JSON (no URL, no token, no name).
- **Staff console:** access token and user in `sessionStorage` (cleared when the tab closes; `AuthContext`), revoked on sign-out (`/auth/token/revoke`); admin-only routes guarded client-side (`AdminRoute`) and server-side (role checks).
