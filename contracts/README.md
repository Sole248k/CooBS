# CooBS Contracts & API Specifications

This directory (`/contracts/`) is the source of truth for API paths, payloads and event schemas across the CooBS platform.

## What is here

| File | Purpose |
| --- | --- |
| `api/CURRENT_API_INVENTORY_AND_USAGE_GUIDE.md` | The 38 endpoints that exist today and who calls them. Some payloads in it differ from the code; see section 1 of the target contract |
| `api/FUTURE_INTEGRATED_API_CONTRACT.md` | The target contract: every public and internal endpoint, payloads, events, enumerations and error codes |
| `api/INTERBANK_TRANSFER_PROPOSAL.md` | The bank-to-bank transfer proposal to send to partner groups |
| `runtime/RUNTIME_SETTINGS.md` | Ports, service URL variables, database names and connection variables every service uses (handshakes H12 and H13) |
| `examples/*.json` | One file per example in the target contract, for Flutter fixtures, Postman bodies and tests. See below |

## Examples

The target contract is the single source. Each JSON block in it has a marker above it naming its file, for example `<!-- example: T1-transfer.request.json -->`. Do not edit the files in `examples/` by hand: the contract is changed first, and Dev 1 re-exports the files from it. `examples/README.md` lists every file with its contract section.

## Still to be written (Dev 1)

- `openapi/<service>.yaml`: machine-readable form of the target contract, one file per service.
- `asyncapi/events.yaml`: machine-readable form of the event section.

## Governance

1. **One editor.** Dev 1 edits files in `/contracts/`. Everyone else proposes changes by pull request.
2. **Freeze.** The frozen commit is tagged `contracts-v1`. After it, renaming, removing or retyping a field is a breaking change: Dev 1 makes it without waiting for agreement, but identifies every consumer, updates the affected code, tests and examples in the same change, and announces it in the handshake thread.
3. **Additive changes only** after the freeze: optional new fields. A breaking change needs a new path version.
4. **Frontend builds on fixtures** exported from the contract examples.
