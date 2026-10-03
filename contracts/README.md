# CooBS Contracts & API Specifications

This directory (`/contracts/`) is the source of truth for API paths, payloads and event schemas across the CooBS platform.

## What is here

| File | Purpose |
| --- | --- |
| `api/CURRENT_API_INVENTORY_AND_USAGE_GUIDE.md` | The 38 endpoints that exist today and who calls them. Some payloads in it differ from the code; see section 1 of the target contract |
| `api/FUTURE_INTEGRATED_API_CONTRACT.md` | The target contract: every public and internal endpoint, payloads, events, enumerations and error codes |
| `api/INTERBANK_TRANSFER_PROPOSAL.md` | The bank-to-bank transfer proposal to send to partner groups |

## Still to be written (Dev 1, Day 2)

- `openapi/<service>.yaml`: machine-readable form of the target contract, one file per service.
- `asyncapi/events.yaml`: machine-readable form of the event section.
- `examples/*.json`: one file per example, exported as Flutter fixtures.

## Governance

1. **One editor.** Dev 1 edits files in `/contracts/`. Everyone else proposes changes by pull request.
2. **Freeze on Day 2.** The commit is tagged `contracts-v1`. After that, renaming, removing or retyping a field needs agreement from every consumer.
3. **Additive changes only** after the freeze: optional new fields. A breaking change needs a new path version.
4. **Frontend builds on fixtures** exported from the contract examples.
