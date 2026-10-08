# Fixture index (FIN-702, mobile)

Existing = already in the project. New = added for FIN-702; shapes follow docs/CONTRACTS.md (my-accounts, RFC-7807 problems) and the existing M1/M3 files. Confirm before freezing.

| File | Endpoint | Used by | Status |
|---|---|---|---|
| A2-A4-login.response.json | POST /auth/customers/login | core | existing |
| K3-K4-account.response.json | GET /api/v1/accounts/{id} | core | existing |
| M1-mobile-dashboard.response.json | GET /api/v1/mobile/dashboard | Customer 360 | existing |
| M2-mobile-timeline.response.json | GET mobile transaction timeline | Remittance result | existing |
| M3-mobile-remittance.request/.response.json | POST remittance | Remittance | existing |
| N1-notifications.response.json | GET notifications | Alerts | existing |
| T1, T2, T3 (customer, staff) | transfers, transaction, timeline | core | existing |
| K5-my-accounts.response.json | GET /api/v1/accounts/my-accounts | Customer 360 cards, Remittance | new |
| K6-account-transactions.response.json | per-account recent transactions (no endpoint in the docs yet) | Customer 360 history | new |
| R1-remittance-options.response.json | remittance form options: banks, fees, limits | Remittance | new |
| E1-insufficient-funds.problem.json | 422 INSUFFICIENT_FUNDS | Remittance | new |
| E2-validation-error.problem.json | 400 VALIDATION_FAILED | Remittance | new |
| K1-kyc-intake-options.response.json | KYC form options | KYC Intake | new |
| K2-kyc-intake.request.json | KYC intake body | KYC Intake | new |
| K2-kyc-intake(.|-verified.|-rejected.)response.json | KYC intake result states | KYC Intake | new |

Money in new files: balances follow the contract (numbers); amounts in transaction/transfer bodies are strings with 2 decimals.
