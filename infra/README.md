# Infrastructure as Code (IaC) - Azure Bicep

**Repository Area:** `/infra`  
**Primary Owner:** Dev 8 (DevOps Lead - Jared)  
**Supporting Contributor:** Dev 1 (Technical Lead / DevOps Support - Carl)  
**Governing Cost Model:** `docs/CLOUD_DECISIONS_AND_AZURE_COST_MODEL.md`  

---

## 1. Overview & Context

This directory contains the production-aligned **Azure Bicep** Infrastructure as Code (IaC) templates for the **CooBS (Cooperative Banking System)** microservices platform. 

It implements the approved **Option A: Containerized Microservices on Azure Kubernetes Service (AKS)** while strictly honoring our FinOps budget constraints:
- **Monthly Budget Ceiling:** `< $450.00 / month`
- **1-Week Live Evaluation Target:** **`~$95.43 total`** (for evaluation & defense week)

All resources are architected for deployment in the **Southeast Asia** region (`southeastasia`) under a single consolidated resource group (`rg-coobs-dev`).

---

## 2. Directory Structure

```text
infra/
├── README.md                     # This file (handover notes & deployment guide)
└── bicep/
    ├── main.bicep                 # Root orchestrator (wires up modules, outputs)
    ├── main.bicepparam            # Environment parameter file (dev/southeastasia)
    └── modules/
        ├── network.bicep          # VNet (10.240.0.0/16), 3 Subnets, NSGs, Private DNS
        ├── monitoring.bicep       # Log Analytics Workspace (capped at 1GB/day) + App Insights
        ├── acr.bicep              # Azure Container Registry (Basic SKU)
        ├── keyvault.bicep         # Azure Key Vault (Standard, RBAC-enabled)
        ├── eventhubs.bicep        # Event Hubs (Standard, 1 TU, Kafka 9093, 13 topics + DLTs)
        ├── redis.bicep            # Azure Cache for Redis (Basic C1, 1GB, TLS 1.2)
        ├── postgres.bicep         # PostgreSQL Flexible (Standard_B2s, 6 service DBs)
        └── aks.bicep              # AKS Cluster (Standard_D4s_v5, Azure CNI, AcrPull role)
```

---

## 3. Module Inventory & Architecture Breakdown

| Module | File | Azure Resource | Configuration & FinOps Specs |
|---|---|---|---|
| **Networking** | `network.bicep` | Virtual Network (`vnet-coobs-dev`) | Address space `10.240.0.0/16`.<br>• `snet-aks`: `10.240.0.0/22` (1024 IPs for pods/nodes)<br>• `snet-postgres`: `10.240.4.0/28` (Delegated to Flexible Server)<br>• `snet-appgw`: `10.240.5.0/24` (Reserved for Ingress Controller)<br>• Private DNS Zone: `coobs.postgres.database.azure.com` linked to VNet. |
| **Monitoring** | `monitoring.bicep` | Log Analytics & App Insights | • `law-coobs-dev`: PerGB2018 SKU, 30-day retention.<br>• **FinOps Guardrail:** `dailyQuotaGb: 1` (caps data ingestion at 1GB/day to stop log-spam charges).<br>• `appi-coobs-dev`: Application Insights linked to Workspace. |
| **Container Registry** | `acr.bicep` | Azure Container Registry | `coobsacr<unique>` (Basic SKU). Admin user enabled for quick cluster bootstrap/testing. |
| **Secret Management** | `keyvault.bicep` | Azure Key Vault | `kv-coobs-<unique>` (Standard SKU). Soft-delete 7 days, RBAC authorization enabled. Central vault for JWT secrets, database credentials, and Kafka connection strings. |
| **Event Streaming** | `eventhubs.bicep` | Event Hubs Namespace | `evh-coobs-dev` (Standard SKU, Kafka 9093 compatible).<br>• **FinOps Guardrail:** 1 Capacity Throughput Unit (TU), Auto-inflate max 2 TUs.<br>• Pre-configures all **13 microservice topics** and **Dead Letter Topics (DLTs)** (`auth-events`, `account-events`, `transaction-events`, `ledger-postings`, `risk-evaluations`, `notification-dispatch`, `audit-log`, etc.) with standard consumer groups (`cg-transaction`, `cg-ledger`, `cg-notification`, `cg-audit`). |
| **Distributed Cache** | `redis.bicep` | Azure Cache for Redis | `redis-coobs-dev` (Basic C1 SKU - 1 GB RAM). Port 6379 disabled; TLS 1.2 enforced on port 6380. Used for idempotency cache, token revocation blacklist, and session state. |
| **Database** | `postgres.bicep` | PostgreSQL Flexible Server | `psql-coobs-dev` (`Standard_B2s`, 2 vCore, 4GB RAM, 64GB storage).<br>• Fully isolated inside `snet-postgres` via Private DNS.<br>• Pre-provisions all **6 service databases**: `identity_db`, `customer_db`, `transaction_db`, `notification_db`, `audit_db`, `batch_db`. |
| **Kubernetes** | `aks.bicep` | Azure Kubernetes Service | `aks-coobs-dev` (`Standard_D4s_v5` - 4 vCore / 16GB RAM, 1 node pool).<br>• Azure CNI networking.<br>• OMS agent integrated with Log Analytics (Container Insights).<br>• System-Assigned Managed Identity automatically granted `AcrPull` on ACR. |

---

## 4. Cost Projection Comparison (FinOps)

This architecture reflects the calculated figures in `docs/CLOUD_DECISIONS_AND_AZURE_COST_MODEL.md`:

```
+------------------------------------+------------------+------------------+
| Resource Component                 | Monthly Estimate | 1-Week Eval Cost |
+------------------------------------+------------------+------------------+
| AKS Node Pool (1x Standard_D4s_v5) | $182.50          | $42.00           |
| PostgreSQL Flexible (Standard_B2s) | $ 37.10          | $  8.54          |
| Azure Cache for Redis (Basic C1)   | $ 30.50          | $  7.00          |
| Event Hubs Standard (1 TU Kafka)   | $ 19.50          | $  4.50          |
| Application Gateway / Ingress      | $113.15          | $ 26.00          |
| Azure Container Registry (Basic)   | $  5.08          | $  1.17          |
| Log Analytics + App Insights       | $ 27.00          | $  6.22          |
+------------------------------------+------------------+------------------+
| TOTAL                              | $414.83 / mo     | $ 95.43 / week   |
+------------------------------------+------------------+------------------+
```

### FinOps Safety Guardrails Applied:
1. **Log Analytics Cap:** Restricted to 1GB/day (`dailyQuotaGb: 1`).
2. **Event Hubs TU Cap:** Auto-inflate ceiling set to maximum 2 TUs (`maximumThroughputUnits: 2`).
3. **No Premium/Multi-AZ Over-provisioning:** Standard networking, single-zone, bursted compute appropriate for the Capstone evaluation.
4. **Fast Teardown:** Entire environment scoped under `rg-coobs-dev` for single-command teardown after evaluation.

---

## 5. Local Validation & Dry Run (Zero Cost)

No Azure charges are incurred by testing and compiling the Bicep code locally.

### 5.1 Local Syntax Validation (Transpilation)
Verify that all 8 modules link properly into `main.bicep`:
```powershell
az bicep build --file infra/bicep/main.bicep
```
*If this returns with exit code 0, the templates are syntactically valid and ready for Azure Resource Manager.*

### 5.2 Dry-Run What-If Analysis (Requires Azure Login)
Preview the planned Azure resource creations without provisioning them:
```powershell
az deployment group what-if `
  --resource-group rg-coobs-dev `
  --template-file infra/bicep/main.bicep `
  --parameters infra/bicep/main.bicepparam
```

---

## 6. Live Deployment & Teardown Runbook

### 6.1 Provisioning (When Authorized)
```powershell
# 1. Login to Azure
az login

# 2. Set the designated subscription
az account set --subscription "<YOUR_SUBSCRIPTION_ID>"

# 3. Create the resource group
az group create --name rg-coobs-dev --location southeastasia

# 4. Deploy the infrastructure
az deployment group create `
  --name coobs-infra-deployment `
  --resource-group rg-coobs-dev `
  --template-file infra/bicep/main.bicep `
  --parameters infra/bicep/main.bicepparam
```

### 6.2 Obtaining Deployment Outputs
After deployment, retrieve cluster credentials and connection strings:
```powershell
# Get AKS credentials for kubectl
az aks get-credentials --resource-group rg-coobs-dev --name aks-coobs-dev

# View outputs (ACR login server, Redis host, Event Hub namespace)
az deployment group show `
  --resource-group rg-coobs-dev `
  --name coobs-infra-deployment `
  --query properties.outputs
```

### 6.3 Post-Evaluation Teardown (FinOps Protection)
Immediately after the final evaluation and defense, delete the resource group to halt all billing:
```powershell
az group delete --name rg-coobs-dev --yes --no-wait
```

---

## 7. Handover Notes & Next Actions for Dev 8 (Jared)

**What Dev 1 (Carl) has scaffolded:**
- [x] Complete modular Bicep architecture under `infra/bicep/modules/`.
- [x] VNet subnetting, Private DNS zone, and NSGs for PostgreSQL and AKS.
- [x] Pre-configured Event Hubs with all 13 microservice topics and consumer groups.
- [x] Pre-configured 6 PostgreSQL databases on the Flexible Server.
- [x] AKS cluster with Azure CNI, managed identity, Container Insights, and ACR pull permission.
- [x] FinOps budget guardrails (1GB/day log quota, 1 TU Event Hubs cap).

**Recommended Next Actions for Jared:**
1. **Azure Pipelines CI Integration (`azure-pipelines.yml`):**
   - Add a PR validation job that runs `az bicep build --file infra/bicep/main.bicep` on pull requests modifying `/infra/*`.
2. **Key Vault Secret Population:**
   - Establish standard secret keys in `kv-coobs-dev` (e.g., `POSTGRES-ADMIN-PASSWORD`, `JWT-SECRET-KEY`, `EVENTHUBS-CONNECTION-STRING`).
3. **Ingress & TLS Setup:**
   - Deploy `ingress-nginx` or Azure Application Gateway Ingress Controller (AGIC) to `snet-appgw`.
   - Setup `cert-manager` for SSL termination.
4. **Kubernetes Configuration Manifests:**
   - Map backend Spring Boot and Flutter environment variables (`SPRING_PROFILES_ACTIVE=prod`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_DATASOURCE_URL`) to Kubernetes ConfigMaps referencing the outputs defined in `main.bicep`.
