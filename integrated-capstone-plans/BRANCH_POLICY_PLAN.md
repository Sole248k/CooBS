# Branch Policy Plan (Azure Repos)

**Status:** plan only. Nothing here has been applied to the repository yet.  
**Applies to:** organisation `CooBankingSystem`, project `CooBS`, repository `CooBS`.  
**Owner:** Dev 8 applies it; Dev 1 approves any later change to it.  
**Goal:** make the ownership rules in the zero-conflict plan something the server enforces, so nobody has to remember them.

---

## 1. Branch model

| Branch | Purpose | Who merges into it | Lives for |
| --- | --- | --- | --- |
| `main` | What we present. Always demo-ready | Pull request from `dev` or `release/*` only | Permanent |
| `dev` | Integration. Everyone's work meets here | Pull request from a work branch | Permanent |
| `feature/<area>-<work item>` | One piece of work, for example `feature/ledger-holds-142` | Its author | Under one day where possible |
| `bugfix/<area>-<work item>` | A defect fix | Its author | Hours |
| `release/<version>` | Cut from `dev` at the Day 8 freeze; P1 and P2 fixes only | Dev 8 creates; pull requests in | Until Day 9 |
| `hotfix/<work item>` | Emergency fix on `main` during rehearsal or demo | Dev 1 or Dev 8 | Hours |

Tags: `contracts-v1` at the Day 2 freeze; `v1.0.0-rc1` at the Day 8 freeze; `v1.0.0` for the presentation.

The imported repository carries about 18 old branches. Delete the ones nobody is using after the team confirms, so the branch list shows only live work.

---

## 2. Policies on `dev`

| Policy | Setting | Why |
| --- | --- | --- |
| Require a pull request | On. No direct pushes | Everything is reviewed and built |
| Minimum reviewers | 1 | Fast enough for a nine-day sprint |
| Allow requestors to approve their own changes | Off | An owner still needs a second pair of eyes |
| Reset votes when new changes are pushed | On | An approval covers the code that was actually reviewed |
| Automatically included reviewers | Required, by path: section 4 | The folder owner must approve changes to their folder |
| Check for linked work items | Required | Every change traces to an Azure Boards item |
| Check for comment resolution | Required | No merge with open review comments |
| Build validation | Required. Pipeline `azure-pipelines.yml`. Trigger automatic. Expires after 12 hours or when `dev` is updated | A stale green build proves nothing |
| Limit merge types | Squash only | One commit per pull request keeps `dev` readable and easy to revert |
| Delete source branch after merge | On by default in the completion dialog | Keeps the branch list clean |

## 3. Policies on `main` and `release/*`

| Policy | Setting |
| --- | --- |
| Require a pull request | On |
| Minimum reviewers | 2, and one must be Dev 1 |
| Allow requestors to approve their own changes | Off |
| Automatically included reviewers | Dev 1 and Dev 8, required, on every path |
| Check for linked work items | Required |
| Check for comment resolution | Required |
| Build validation | Required, same pipeline, expires immediately when the target changes |
| Limit merge types | Merge commit (no fast-forward), so each promotion from `dev` is one visible merge |

Use the same settings for `release/*` by adding the policy to the branch pattern `release/*`.

---

## 4. Automatically included reviewers by path

One rule per row, each set to **Required**. Roles map to people as follows: Dev 1 Carl, Dev 2 Hans, Dev 3 Wayne, Dev 4 Ysa, Dev 5 April, Dev 6 Neo, Dev 7 Gabo, Dev 8 Jared. Carl also supports Dev 8, so add him as the second reviewer on the Dev 8 rules. Where a pull request touches two owners' folders, both are added.

| Path filter | Required reviewer |
| --- | --- |
| `/contracts/*` | Dev 1 |
| `/backend/orchestration-service/*` | Dev 1 |
| `/postman/*` | Dev 1 |
| `/backend/auth-service/*` | Dev 2 |
| `/backend/customer-service/*` | Dev 2 |
| `/backend/account-service/*` | Dev 2 |
| `/backend/transaction-service/*` | Dev 3 |
| `/backend/ledger-service/*` | Dev 3 |
| `/backend/notification-service/*` | Dev 3 |
| `/backend/risk-engine/*` | Dev 4 |
| `/backend/core-mock/*` | Dev 4 |
| `/database/*` | Dev 5 |
| `/backend/batch-service/*` | Dev 5 |
| `/backend/audit-service/*` | Dev 5 |
| `/app/pubspec.yaml;/app/lib/core/*;/app/android/*;/app/web/*` | Dev 6 |
| `/app/lib/features/monitoring/*` | Dev 6 |
| `/app/lib/features/*;!/app/lib/features/monitoring/*` | Dev 7 |
| `/app/assets/fixtures/*` | Dev 7 |
| `/docker-compose.yml;/.env.example;/azure-pipelines.yml;/.azuredevops/*` | Dev 8 |
| `/backend/pom.xml;/backend/*/Dockerfile` | Dev 8 |
| `/backend/api-gateway/*` | Dev 8 |
| `/infra/*;/monitoring/*;/scripts/*` | Dev 8 |
| `/integrated-capstone-plans/*` | Dev 1 |

Two packages sit inside another owner's service. Azure Repos adds both reviewers:

| Path filter | Required reviewers | Note |
| --- | --- | --- |
| `/backend/ledger-service/src/main/java/com/group2/fse/ledger_service/reversal/*` | Dev 4 and Dev 3 | Dev 4 writes; the service rule adds Dev 3 |
| `/backend/transaction-service/src/main/java/com/group2/fse/transaction_service/interbank/*` | Dev 4 and Dev 3 | Same |

**When the owner is the author.** The owner cannot approve their own pull request, so the path rule cannot be satisfied by them. Add each owner's pairing partner from the zero-conflict plan as a second reviewer on the same rule, with "minimum 1 of these reviewers". Then either person can approve the other's work.

---

## 5. Repository permissions

| Setting | Value |
| --- | --- |
| Force push | Denied for everyone on `main`, `dev`, `release/*` |
| Bypass policies when completing pull requests | Denied for everyone. Allowed only for Dev 1 and Dev 8, for a demo-day emergency, and each use is announced in the channel |
| Bypass policies when pushing | Denied for everyone |
| Create branch | Allowed only under `feature/`, `bugfix/`, `release/`, `hotfix/` |
| Delete or rename `main`, `dev` | Denied |

---

## 6. Pull request rules the server cannot enforce

- **Size:** aim for under 400 changed lines. Split anything larger.
- **Title:** `<area>: <what changed>`, for example `ledger: place and release holds`.
- **Description:** what, why, how it was tested. Link the work item with `AB#<id>`.
- **Contract changes:** a pull request that changes an agreed payload updates `contracts/` in the same change, lists every known consumer and what was updated for them, and links the handshake thread. It does not wait for each consumer's approval; a silent change is what is not allowed.
- **Shared files:** requested from the single editor; do not include them in a feature pull request.
- **Review time:** a reviewer responds within two working hours during the sprint. After that the author pings the pairing partner.
- **Template:** move `.github/pull_request_template.md` to `.azuredevops/pull_request_template.md` so Azure Repos picks it up.

---

## 7. Rollout order

Apply in this order, so nobody is locked out while the pipeline is still being proved.

| Step | Action | Done when |
| --- | --- | --- |
| 1 | Create the pipeline in Azure Pipelines from `azure-pipelines.yml` and run it once on `dev` | One green run |
| 2 | Confirm the organisation has a hosted parallel job; if not, register a self-hosted agent | The run in step 1 started without waiting |
| 3 | Add the reviewer-count, comment-resolution and work-item policies to `dev` | A test pull request shows them |
| 4 | Add build validation to `dev` | The test pull request triggers the pipeline |
| 5 | Add the path reviewer rules | The test pull request adds the right owner |
| 6 | Apply the stricter set to `main` | — |
| 7 | Set the permissions in section 5 | A direct push to `dev` is rejected |
| 8 | Delete the unused imported branches | — |

**Step 2 matters.** A new Azure DevOps organisation may have no free Microsoft-hosted parallel job for a private project until a free grant is requested, and that request can take days. Check this first. The fallback is a self-hosted agent on one team machine with Docker and Java 21; the pipeline needs only the `pool` section changed.

---

## 8. Applying it

In the web portal: **Project settings → Repos → Repositories → CooBS → Policies → Branch policies**, then pick the branch.

The same can be scripted with the Azure CLI `azure-devops` extension, which is easier to review and repeat. These are the commands to start from; check each with `--help` before running, since I have not run them against this organisation.

```bash
az devops configure --defaults organization=https://dev.azure.com/CooBankingSystem project=CooBS
```

```bash
az repos show --repository CooBS --query id -o tsv
```

```bash
az repos policy approver-count create --repository-id <repo-id> --branch dev --minimum-approver-count 1 --creator-vote-counts false --allow-downvotes false --reset-on-source-push true --blocking true --enabled true
```

```bash
az repos policy build create --repository-id <repo-id> --branch dev --build-definition-id <pipeline-id> --display-name "CooBS validation" --manual-queue-only false --queue-on-source-update-only true --valid-duration 720 --blocking true --enabled true
```

```bash
az repos policy required-reviewer create --repository-id <repo-id> --branch dev --path-filter "/backend/ledger-service/*" --required-reviewer-ids "<dev3-email>;<dev2-email>" --message "Ledger owner review" --blocking true --enabled true
```

```bash
az repos policy merge-strategy create --repository-id <repo-id> --branch dev --allow-squash true --allow-no-fast-forward false --allow-rebase false --allow-rebase-merge false --blocking true --enabled true
```

```bash
az repos policy comment-required create --repository-id <repo-id> --branch dev --blocking true --enabled true
```

```bash
az repos policy work-item-linking create --repository-id <repo-id> --branch dev --blocking true --enabled true
```

---

## 9. Open points

1. Whether a hosted parallel job is available (section 7, step 2).
2. Whether squash-only on `dev` suits the team; the alternative is to allow rebase as well.
3. Which imported branches can be deleted.

These are tracked with the other open decisions in section 7 of the zero-conflict plan.
