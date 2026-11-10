# CooBS Branch Teller Portal — Specification & Implementation Plan

**Version:** 1.1.0 (corrected after code verification)
**Status:** DRAFT — awaiting approval. No application code changed.
**Client:** React web portal, `frontend/` (`TellerWorkspace.tsx`)
**Users:** `ROLE_TELLER` (`teller_alice`, `teller_bob`)
**Sources checked:** backend controllers and services, `api-gateway/application.yml`, `database/seed/SEED_REFERENCE.md`, `frontend/src/**`

> **Changes since 1.0.0**
> - Reversal backend is a stub and is not reachable through the gateway, so it is now a **blocker** and no longer counted as ready.
> - Customer 360 returns an empty accounts list because of an internal auth bug, so it is now a **blocker** for accounts and totals.
> - "No gateway change" was wrong. One routing fix is needed for reversal (§2).
> - Tellers can only **list** closure requests. Approve and reject are ADMIN-only.
> - Batch path corrected to `/api/v1/batch/eod/runs`.
> - Transfer payload for `transaction-service` uses `destinationAccountNumber`.
> - Seed tier corrected: `john_doe` is `TIER_3_FULL`.
> - The existing "High-Value Transfers" tab is local mock data with no backend.

---

## 1. Scope

The work is frontend-only, in `frontend/src`: `types/index.ts`, `services/api.ts`, `components/TellerWorkspace.tsx`, plus reuse of `components/AuditView.tsx`.

Backend and gateway items in §2 are **not** part of this work. They belong to the listed owners. Until those items land, the frontend uses its existing offline fallback, and the UI marks those features as "simulated".

---

## 2. Backend and gateway blockers (outside the frontend)

| # | Blocker | Evidence | Effect on teller UI | Owner (per work packages) |
|---|---|---|---|---|
| B1 | **Reversal is a stub.** It returns a fake `REV-xxxx` / `REVERSED` and never calls the orchestrator or ledger. | `transaction-service/.../ReversalServiceImpl.java` L19–31 | The reversal button "succeeds", but no balances change. Demo Scenario 7 cannot be shown for real. | Dev 3 / Dev 4 |
| B2 | **Reversal is not routed.** The controller listens on `/api/v1/reversals`, but the gateway only routes `/api/v1/transactions/**`. | `ReversalController.java` L15; `application.yml` L129–140 | 404 through the gateway. | Dev 8 (add a gateway route) **or** Dev 3 (move the controller under `/api/v1/transactions/{id}/reversal`) |
| B3 | **Customer 360 accounts come back empty.** The 360 service calls `/internal/v1/customers/{id}/accounts` with no token, but that endpoint requires `hasRole('SERVICE')`. The error is caught and an empty list is returned. `recentTransactions` and `spendingBreakdown` are hard-coded empty. | `Customer360ServiceImpl.java` L89–129; `AccountInternalController.java` L22 | The profile, KYC, tier and scores show. Accounts, totals and recent activity are blank. | Dev 2 |
| B4 | **The staff timeline has no role guard.** `?view=staff` returns 11 stages to any caller, including customers. | `TimelineServiceImpl.determineIsStaffView` L269–272 | No effect on the teller UI. It is an RBAC gap. | Dev 3 |
| B5 | **The dual-control queue has no backend.** Pending high-value transfers exist only in `api.ts` mock arrays. | `api.ts` L1001–1037 | The existing tab is fake. | Decision needed: keep it as a labeled demo, remove it, or add an endpoint |

**Gateway answer (corrected):** The frontend adds no gateway code. B2 does need **one** gateway route **or** a controller path change by its owner. Every other teller feature uses routes that already exist.

---

## 3. Feature status (verified)

| ID | Feature | Endpoint (real path) | Teller allowed? | Backend ready? | Frontend work |
|---|---|---|---|---|---|
| G1 | Customer 360 lookup | `GET /api/v1/customers/{id}/360` | Yes | **Partial** (B3) | Build the panel. Show accounts, or "unavailable" when the list is empty |
| G2 | 11-stage staff tracker | `GET /api/v1/transactions/{ref}/timeline?view=staff` | Yes | Yes | Build the stepper with polling |
| G3 | Assisted counter transfer | `POST /api/v1/ledger/transfers` (`destinationAccountId`) **or** `POST /api/v1/transactions/transfers` (`destinationAccountNumber`) | Yes | Yes | Build the form. Pick one endpoint (§5) |
| G4 | Transfer reversal | `POST /api/v1/reversals` (not routed) | Yes | **No** (B1, B2) | Build the form behind a "simulated" label until B1 and B2 land |
| G5 | Audit statement and chain verify | `GET /api/v1/audit/accounts/{id}/statement`, `GET /api/v1/audit/verify-chain/{id}` | Yes | Yes | Mount the existing `AuditView.tsx` |
| G6 | Add or list account flags | `POST` / `GET /api/v1/accounts/{id}/flags` | Add and list: yes. Remove: **ADMIN only** | Yes | Add a flag modal. No remove button for tellers |
| G7 | Closure requests | `GET /api/v1/accounts/closure-requests?status=PENDING` | **List only.** Approve and reject are ADMIN-only | Yes | Read-only list |
| G8 | EOD batch monitor | `GET /api/v1/batch/eod/runs`, `GET /api/v1/batch/eod/runs/{id}` | Read: yes. Trigger: ADMIN only | Yes | Read-only status card |

Already in `TellerWorkspace.tsx` and kept as is: the KYC queue, cash deposit and withdrawal, open account, and freeze/activate.

---

## 4. Seed data to demo against (from `SEED_REFERENCE.md`)

| Use | Seed |
|---|---|
| Teller login | `teller_alice` / `Password123!` |
| Customer 360 showcase | Customer 4 `juan_delacruz`: savings, current, loan, 30-day history |
| Baseline 360 | Customer 1 `john_doe`: `TIER_3_FULL`, risk 0.15, credit 750 |
| `TIER_2_STANDARD` example | Customer 13 `ricardo_magbanua` |
| Pending KYC update | Customer 6 `jose_bautista` |
| Frozen and flagged account | Customer 8 `ramon_villanueva` |
| Pending closure | Customer 11 `paolo_navarro` |
| Reversed transfer and in-flight hold | Customer 12 `teresita_ramos` |

> `E2E_CHECKLIST.md` item 1.4 expects `TIER_2_STANDARD` for customer 1. That conflicts with the seed, so the checklist or the seed needs correcting (owner: Dev 5 / Dev 1).

---

## 5. TypeScript contracts to add (`frontend/src/types/index.ts`)

These match the backend DTOs as they exist today.

```typescript
// customer-service Customer360Response (balances are strings)
export interface Customer360Account {
  accountId: number;
  accountNumber: string;
  accountType: string;
  currency: string;
  status: string;
  availableBalance: string;
  ledgerBalance?: string;
  heldAmount?: string;
}
export interface Customer360Response {
  customerId: number;
  displayName: string;
  membershipNumber?: string;
  kycStatus: string;
  kycTier?: string;
  creditScore?: number;
  riskScore?: string;
  accounts: Customer360Account[];
  totals: { totalDeposits: string; totalLoans: string };
  asOf?: string;
}

// transaction-service TimelineResponseDto / TimelineStageDto
export interface TimelineStage {
  stageNumber: number;
  stageKey: string;
  title: string;
  service?: string;
  status: string;      // COMPLETED | IN_PROGRESS | FAILED | PENDING
  timestamp?: string;
  detail?: string;
}
export interface TimelineResponse {
  transactionId: string;
  trackingReference: string;
  view: string;
  overallStatus: string;
  currentStage: number;
  totalStages: number;
  stages: TimelineStage[];
  failureMessage?: string;
}

// transaction-service ReversalRequestDto / ReversalResponseDto
export interface ReversalRequest { originalReferenceNo: string; amount?: number; reason: string; }
export interface ReversalResponse { reversalId: string; originalReferenceNo: string; amount: number; status: string; timestamp: string; }

// transaction-service TransferRequestDto (saga path)
export interface SagaTransferRequest {
  sourceAccountId: number;
  destinationAccountNumber: string;
  amount: number;
  remarks?: string;
}
// batch-service BatchRunDetailsResponseDto / BatchStepResponseDto
export interface BatchStep {
  stepNo: number;
  stepName: string;
  status: string;      // COMPLETED | RUNNING | FAILED | SKIPPED
  itemsProcessed?: number;
  totalAmount?: string;
  durationMs?: number;
  message?: string;
}
export interface BatchRunDetails {
  batchRunId: number;
  businessDate: string; // YYYY-MM-DD
  status: string;      // COMPLETED | RUNNING | FAILED
  triggeredBy?: number;
  startedAt?: string;
  finishedAt?: string;
  steps: BatchStep[];
}
```

**Counter transfer endpoint choice:** `POST /api/v1/transactions/transfers` is recommended, because it goes through the saga and appears in the 11-stage tracker (G2). The ledger path posts directly and has no timeline.

---

## 6. Implementation phases (after approval)

| Phase | Work | Files |
|---|---|---|
| 1 | Add types (§5). Add `getCustomer360`, `getStaffTimeline`, `submitSagaTransfer`, `reverseTransaction`, `addAccountFlag`, `getAccountFlags`, `getBatchRuns`, each with the existing offline fallback pattern | `types/index.ts`, `services/api.ts` |
| 2 | Customer 360 panel. Counter transfer form in the cash tab | `TellerWorkspace.tsx` |
| 3 | 11-stage tracker with 3 s polling. Reversal form labeled "simulated — backend B1/B2 pending" | `TellerWorkspace.tsx` |
| 4 | Mount `AuditView`. Flag modal (add only). Read-only closure list. Read-only batch card | `TellerWorkspace.tsx` |
| 5 | `npm run build` in `frontend/`. Walk through §4 seeds against the live gateway and offline mode | — |

Proposed tabs: **Customer 360 · Counter Desk · Queues (KYC, closures read-only) · Accounts · Tracker & Reversal · Audit & Batch**.

---

## 7. Open decisions for you

1. Dual-control tab (B5): keep it as a labeled demo, remove it, or request an endpoint?
2. Reversal (B2): gateway route (Dev 8) or controller path move (Dev 3)?
3. Should the frontend ship G1 and G4 now with "partial"/"simulated" labels, or wait for B1–B3?

No code in `frontend/src` or `backend/` has been modified.
