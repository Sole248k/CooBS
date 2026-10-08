# CooBS Staff Console — Comprehensive Context & Handoff Specification

**Version:** 1.0 (Handoff)  
**Date:** 2026-10-08  
**Purpose:** Use this document as the prompt/context in a new Antigravity session when opening your project folder. It contains every requirement, design token, architectural contract, file location, and outstanding implementation item.

---

## 1. System Overview & Product Separation

We are building the **CooBS Enterprise Banking Staff Console** (React 18 + TypeScript + Vite + Tailwind CSS) for Branch Tellers and System Administrators.

### Crucial Visual Separation: Mobile vs Staff Console
The overall banking ecosystem consists of two distinct frontend clients:
1. **Flutter Mobile Banking App (`app/`)**: For retail customers. Uses a **Teal / Turquoise** brand identity (`#0D9488`, `#14B8A6`).
2. **React Staff Console (`frontend/`)**: For internal institutional staff (Tellers & Admins). Uses a **Corporate Navy & Electric Blue** palette (`#1E3A5F`, `#2563EB`) on a crisp light canvas (`#F0F4F8`). **No dark mode for staff** (per the UXDA reference approved by user).

---

## 2. Approved Design System & Visual Guidelines

Inspired by UXDA's institutional banking console dashboard:

### Color Palette ("Corporate Navy & Pure Slate")
- **Canvas / Outer Background:** `#F0F4F8` (soft cool gray)
- **Cards & Data Tables:** `#FFFFFF` (pure white surface with 12px border radius, 1px `#E2E8F0` border, subtle `0 1px 3px rgba(0,0,0,0.06)` shadow)
- **Top Header Bar (56px):** White `#FFFFFF`, border-bottom 1px `#E2E8F0`
- **Left Navigation Sidebar (64px):** Deep Navy `#1E3A5F` with rounded right corners (16px)
- **Primary Brand / Headers / Data Readouts:** Deep Navy `#1E3A5F`
- **Primary Action / CTA:** Corporate Blue `#2563EB` (hover: `#1D4ED8`)
- **Secondary / Helper Text / Monospace Hashes:** Cool Slate `#64748B`

### Semantic Status Tokens
- **Success / Verified / Intact / Active:** `#10B981` (Emerald) | Background: `#D1FAE5`
- **Warning / Pending / In Review:** `#F59E0B` (Amber) | Background: `#FEF3C7`
- **Critical / Frozen / DLQ / Reversal Failed:** `#EF4444` (Crimson) | Background: `#FEE2E2`
- **Closed / Dormant / Muted:** `#94A3B8` (Slate) | Background: `#F1F5F9`

### Role Badges
- **Branch Teller:** Teal pill (`#0F766E` text on `#CCFBF1` background)
- **System Admin:** Purple pill (`#6D28D9` text on `#EDE9FE` background)

### Formatting Standards
- **Currency:** Display as `₱` (Philippine Peso), formatted with two decimal places (e.g., `₱1,420,500.00`). Backend sends strings (e.g. `"2500.00"`), parsed with exact decimal arithmetic.
- **Account Numbers:** Fixed format: `ACC_10000001`, `LN_10000007`.
- **Transaction References:** `TX-20261003-8F3A21C0`, reversals prefixed with `RV-`.
- **Hashes & Keys:** Truncated for display (`7f8a12c0...3d9f`) in `JetBrains Mono` with copy-to-clipboard button.
- **Density:** 8px grid, 36px table row height, tabular monospace numbers (`font-variant-numeric: tabular-nums`).

---

## 3. Role-Based Access Control (RBAC) Matrix

Every session is authenticated via `POST /api/v1/auth/staff/login`. The role is extracted from `authData.roles` (`ROLE_TELLER` or `ROLE_ADMIN`).

| Screen / Feature | Teller (`ROLE_TELLER`) | Admin (`ROLE_ADMIN`) |
| :--- | :--- | :--- |
| **WEB-01: Sign-In** | Yes | Yes |
| **WEB-02: Header & Shell** | Yes (`[Branch Teller]` badge) | Yes (`[System Admin]` badge) |
| **WEB-03: Customer 360** | Yes (search, view balances, risk & credit score) | Yes |
| **WEB-04: Counter Desk** | Cash Deposit, Cash Withdrawal, **Assisted Transfer** | Cash Deposit, Cash Withdrawal (**No Assisted Transfer**) |
| **WEB-05: Queues (KYC)** | View, Approve, Reject (with reason) | View, Approve, Reject (with reason) |
| **WEB-05: Queues (Closures)** | View only (Read-only) | View, Approve, Reject (with reason) |
| **WEB-06: Accounts** | Open Account, Freeze, Activate, Add Flag | Open Account, Freeze, Activate, Add Flag, **Remove Flag** |
| **WEB-07: Tracker & Reversal** | 11-stage trace, Reverse POSTED transfer | 11-stage trace, Reverse POSTED transfer |
| **WEB-08: Audit & Merkle** | View Statement, Verify Chain, Daily Seal | View Statement, Verify Chain, Daily Seal |
| **WEB-09: End of Day (EOD)** | View Batch Runs & Steps (Read-only) | View Runs + **Trigger EOD Batch Run** |
| **WEB-10: Dead Letter Queue** | **Hidden / Access Denied** | View DLQ Messages, **Replay to Kafka** |

---

## 4. Screen Specifications (WEB-01 to WEB-10)

1. **WEB-01 Staff Sign-in**: Username, password, "Sign In" CTA. No registration, no password reset, no branch selection field (branch derived from token).
2. **WEB-02 Header & Shell**: Brand logo, Branch pill (`BR-0104 MAKATI`), Gateway latency (`Gateway: 3ms`), Role badge, User (`@teller_alice`), Logout. 64px navy sidebar with 8 navigation links.
3. **WEB-03 Customer 360**: Search bar (`Ctrl+K`), Customer Profile card (KYC status pill, Tier pill, Risk Score `12/100`, Credit Score `832`), 4 metric summary cards (Total Ledger, Available, Held, 30-day velocity), Accounts table with copyable account IDs, Recent transfers list, Spending category chart.
4. **WEB-04 Counter Desk**: Segmented tabs: `[Cash Deposit]` | `[Cash Withdrawal]` | `[Assisted Transfer]` (Teller only). Account selector, prominent `₱` amount input, denomination calculator chips (+₱1,000, +₱5,000, +₱10,000, Max), Remarks, Confirmation modal with printable receipt & QR.
5. **WEB-05 Queues**: KYC Updates list & Account Closures list. Reject triggers mandatory reason modal. Admin sees closure decision buttons; Teller is read-only.
6. **WEB-06 Accounts**: Open Account slide-over drawer (Account type: `SAVINGS`, `CHECKING`, `CURRENT`, `LOAN`; no initial deposit field as balance starts at ₱0.00). Account directory table with status pills (`ACTIVE`, `FROZEN`, `CLOSED`) and active flag chips (`AML_WATCH`, `DORMANT_WARNING`). Add/Remove flag modal.
7. **WEB-07 Tracker & Reversal**: Search by `referenceNo` or UUID. Full 11-stage vertical pipeline (`INITIATED`, `VALIDATED`, `AUTHENTICATED`, `FRAUD_CHECK`, `LIMIT_CHECK`, `FUNDS_CHECK`, `AUTHORIZED`, `POSTED`, `LEDGER_UPDATE`, `NOTIFICATION`, `RECONCILIATION`). Live polling toggle (3s). "Initiate Reversal" modal requiring reason (enabled only when transfer is `POSTED`).
8. **WEB-08 Merkle Audit**: Chain Integrity hero banner (`100% INTACT`), Daily Closing Seal card (SHA-256 Merkle root hashes), Statement table showing previous and current hashes per transaction with copy actions.
9. **WEB-09 End of Day Batch**: List of EOD batch runs with duration and status (`COMPLETED`, `HALTED`, `RUNNING`). 6-step progress pipeline (`INTEREST_ACCRUAL` → `SERVICE_FEES` → `STANDING_INSTRUCTIONS` → `LOAN_AMORTIZATION` → `GL_RECONCILIATION` → `AUDIT_SEAL`). "Trigger EOD Run" button (Admin only).
10. **WEB-10 Dead Letter Queue**: Admin-only table of unprocessable Kafka messages. Original topic, failure code, payload JSON preview, and "Replay to Kafka" action.

---

## 5. Existing Scaffolded Files & Current Location

The foundational architecture has already been written to disk at:
`C:\Users\rcd83773\.gemini\antigravity\scratch\coobs-staff-console\frontend\`

### Completed Files:
- `package.json` — Vite, React 18, React Router DOM 6, Lucide React, Tailwind CSS, TypeScript.
- `vite.config.ts` — `@/` alias resolution and `/api` proxy.
- `tsconfig.json` — Strict TypeScript path mapping.
- `tailwind.config.ts` — Full custom design tokens (navy, canvas, status, badges, font families).
- `postcss.config.js` — Tailwind & Autoprefixer plugin configuration.
- `index.html` — Configured with Inter and JetBrains Mono fonts.
- `src/index.css` — Base styles, status pill classes, role badge styles, scrollbar styling.
- `src/types/staff-bff.ts` — 100% complete TypeScript contracts matching the backend API contract.
- `src/lib/utils.ts` — Utility functions (`formatCurrency`, `formatTimestamp`, `truncateHash`, `generateUUID`, `getStatusClass`, `cn`).

---

## 6. Next Steps for the New Conversation

When opening a new conversation in your project workspace:
1. Copy or move `C:\Users\rcd83773\.gemini\antigravity\scratch\coobs-staff-console\frontend\` into your project directory.
2. Complete the remaining frontend components:
   - `src/contexts/AuthContext.tsx` (manages staff login, tokens, role permissions)
   - `src/services/api.ts` (API client for `/api/v1/staff/**` and `/api/v1/auth/**`)
   - `src/components/shell/` (`AppLayout.tsx`, `AppHeader.tsx`, `Sidebar.tsx`, `RoleBadge.tsx`, `StaffSignIn.tsx`)
   - `src/components/customer/Customer360.tsx`
   - `src/components/counter/CounterDesk.tsx`
   - `src/components/queues/` (`KYCQueue.tsx`, `ClosureQueue.tsx`)
   - `src/components/accounts/` (`AccountDirectory.tsx`, `OpenAccountDrawer.tsx`)
   - `src/components/tracker/TransactionTracker.tsx`
   - `src/components/audit/AuditStatement.tsx`
   - `src/components/admin/` (`EODBatchPipeline.tsx`, `DeadLetterQueue.tsx`)
   - `src/App.tsx` (routing with RBAC guards)
   - `src/main.tsx` (React root render)
3. Run `npm install` and `npm run dev`.
