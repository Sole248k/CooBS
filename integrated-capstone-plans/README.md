# Integrated Capstone Plans

The current planning set for the CooBS expansion. Older architecture documents are deliberately left out; if a document is not listed here, it is not current.

## Read in this order

| #   | File                                                                                                       | What it answers                                                                                                   |
| --- | ---------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| 1   | [`ZERO_CONFLICT_8_DEV_EXECUTION_PLAN.md`](ZERO_CONFLICT_8_DEV_EXECUTION_PLAN.md)                           | Who does what on each of the nine days, and the rules that prevent merge conflicts                                |
| 1b  | [`DAY_BY_DAY_PARALLEL_EXECUTION_ROADMAP.md`](DAY_BY_DAY_PARALLEL_EXECUTION_ROADMAP.md)                     | Current state assessment (post-commit 6a4c293), zero-conflict model, and exact day-by-day deliverables (Days 2-9) |
| 2   | [`PER_DEV_TECHNICAL_WORK_PACKAGES.md`](PER_DEV_TECHNICAL_WORK_PACKAGES.md)                                 | File-level work per developer, code review findings, and the handshakes between developers                        |
| 3   | [`SERVICE_SCOPE_SCENARIOS_AND_ACTIONS.md`](SERVICE_SCOPE_SCENARIOS_AND_ACTIONS.md)                         | Per service: what it owns, what it must not do, which role can do which action, and what happens in each scenario |
| 3b | [`BRANCH_POLICY_PLAN.md`](BRANCH_POLICY_PLAN.md) | Branch model, pull request policies and reviewer-by-path rules for Azure Repos. A plan; not applied yet |
| 4   | [`../contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md`](../contracts/api/FUTURE_INTEGRATED_API_CONTRACT.md) | Every endpoint, payload, event, enumeration and error code                                                        |
| 5   | [`../contracts/api/INTERBANK_TRANSFER_PROPOSAL.md`](../contracts/api/INTERBANK_TRANSFER_PROPOSAL.md)       | The bank-to-bank proposal for partner groups                                                                      |
| 6   | [`../contracts/runtime/RUNTIME_SETTINGS.md`](../contracts/runtime/RUNTIME_SETTINGS.md)                     | Ports, service URL variables, database names and connection variables (handshakes H12 and H13)                    |

## When two documents disagree

1. The API contract wins on paths, payloads, field names and enumerations.
2. The per-developer work packages win on which files are touched and by whom.
3. The zero-conflict plan wins on dates and ownership.

Report the disagreement to Dev 1 so the losing document gets fixed.

## Status

- All of this is a **draft until the we agree on it**. Owners must review their own sections.
- Diagram code has not been test-rendered in Eraser. The Azure Boards files have not been test-imported.
- The contract is prose with JSON examples. The examples are exported to `contracts/examples/`, one file each. OpenAPI and AsyncAPI files are still to be written.
- The contract is frozen at the tag `contracts-v1`.

## Settled decisions

- Frontend is Flutter for web and Android in `app/`. The React dashboard in `frontend/` is frozen as the staff console and fallback.
- Roles follow the v2 workbook: Dev 5 database, Dev 8 DevOps and cloud, no QA role.
- Cloud: gateway, auth (`identity_db`), customer (`customer_db` - profile, KYC, beneficiaries), transaction, orchestration, risk, notification. On-premises: ledger, account (account lifecycle, flags, closures), batch, audit.
- One API gateway: Spring Cloud Gateway. No Azure API Management.
- Schemas and data are disposable: rebuilt from one seed source, never migrated in place. Tables are created by the init scripts under `database/`; services carry no migration files.
- Development is local first, on one Docker network. Splitting the cloud and on-premises zones into separate networks is deferred to a later phase.
- A handshake change does not wait for consumers to agree. It is written into `contracts/`, updates the consumers it affects, and is announced.
- Source control, boards and pipelines are in Azure DevOps.
