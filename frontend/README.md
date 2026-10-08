# CooBS Teller Web Portal (`frontend/`)

The web frontend for the **CooBS Core Banking System**, built with **React 19**, **TypeScript**, and **Vite**.

Designed specifically for **Branch Tellers, Operations Staff, and Compliance Officers** for account lifecycle management, KYC verification, teller cash operations, and technical saga audit tracking.

> **Architecture Note:**
> Retail members use the dedicated **Flutter Mobile App** in `app/`. The web portal in `frontend/` is restricted to authorized branch staff and back-office operations.

---

## 1. Quick Start

### Install Dependencies
```bash
npm install
```

### Start Development Server
```bash
npm run dev
```
The application will launch on **[http://localhost:3000](http://localhost:3000)**.

> **Proxy Configuration:** Requests to `/api/*` and `/actuator/*` are automatically reverse-proxied by Vite to the **Spring Cloud Gateway** at `http://localhost:8080`.

---

## 2. Testing Credentials

| Role | Username | Password | Purpose |
| :--- | :--- | :--- | :--- |
| **Branch Teller** | `teller_alice` | `Password123!` | Primary staff account for teller operations and inquiries |
| **Supervisor** | `supervisor_bob` | `Password123!` | Elevated permissions / override approvals |

---

## 3. Scripts

- `npm run dev`: Starts the local Vite development server with hot module reloading.
- `npm run lint`: Runs fast Oxlint static code analysis.
- `npm run build`: Type-checks and builds the production bundle into `dist/`.
- `npm run preview`: Previews the production build locally.

---

## 4. Key Security & Compliance Features

- **Session Inactivity Guard:** 15-minute inactivity watcher automatically revokes the session on the backend and logs out idle staff.
- **Session-Scoped Token Storage:** Auth tokens are stored exclusively in `sessionStorage` (never persisting in `localStorage`), preventing token survival across browser close.
- **Server-Side Token Revocation:** Explicit logout calls `POST /api/v1/auth/token/revoke` to immediately blacklist JWTs.
- **Regulatory Ceiling:** Transfer operations enforce the ₱500,000 regulatory limit and strict input sanitization.

---

## 5. Comprehensive Testing Guide
For full details on testing across web and mobile, see the root [TESTING_GUIDE.md](../TESTING_GUIDE.md).
