# Infrastructure: the demo deployment in Azure

**Area:** `/infra`. Dev 8 (Jared) leads; Dev 1 (Carl) supports.

**Status: compiles with Bicep 0.48.1 and passes Azure's what-if against the real subscription (9 October 2026: 33 resources to create, the registry untouched); never deployed.** Nothing in `infra/bicep` has created a resource yet. `infra/k8s` has its own README and status.

This is the in-depth architecture cut to what an Azure free trial allows. It is drawn in `docs/architecture-diagrams/demo-deployment.eraserdiagram`.

## What the free trial allows

Read from the subscription on 8 and 9 October 2026:

| Limit | Value | What it forces |
| --- | --- | --- |
| vCPUs in the region | 4 in total | One 2 vCPU AKS node and one 2 vCPU VM, with nothing to spare. No autoscaling, no node upgrade |
| Public IP addresses | 3 | Exactly three are used: AKS outbound, the public gateway, the on-premises NAT gateway |
| VM sizes | `Standard_E2as_v5` and `Standard_B2as_v2` only in zone 3 | Indonesia Central, zone 3. Southeast Asia offers no usable size |
| Kubernetes | 1.34 to 1.36 on the Free tier (1.31 to 1.33 are long-term-support only) | 1.35 |

## What is created

Everything goes into the resource group `CooBS-rsg` in Indonesia Central. The container registry `CooBS` already exists there and is referenced, not created.

| Module | Creates |
| --- | --- |
| `network.bicep` | Cloud VNet `10.240.0.0/16` (`snet-aks` `10.240.0.0/20`, `snet-private-endpoints` `10.240.16.0/24`). On-premises VNet `10.250.0.0/16` (`snet-core` `10.250.0.0/24`). Peering in both directions. The NSG on `snet-core`. A NAT gateway for the on-premises subnet |
| `monitoring.bicep` | Log Analytics workspace capped at 1 GB a day, and Application Insights |
| `keyvault.bicep` | Key Vault with RBAC authorisation, the Secrets Officer role for one person, and the secret `cloud-db-password` |
| `sql.bicep` | Azure SQL logical server and `identity_db`, `customer_db`, `transaction_db`, `notification_db` at S0, collation `Latin1_General_100_CI_AS_SC_UTF8`. Reachable only from `snet-aks` |
| `redis.bicep` | Azure Managed Redis `Balanced_B0`, one node, TLS on port 10000, access keys on. A private endpoint and a private DNS zone linked to both VNets. The secret `redis-key` |
| `aks.bicep` | AKS, Free tier, one `Standard_E2as_v5` node in zone 3. AcrPull on the registry, Network Contributor on the cloud VNet |
| `onprem-vm.bicep` | One `Standard_B2as_v2` VM in zone 3 at `10.250.0.4`, no public address, Ubuntu 24.04, Docker installed on first boot. AcrPull on the registry and Key Vault Secrets User on the vault |

Not created: Event Hubs (Kafka runs in the cluster), a VPN gateway (peering stands in for it), Front Door (added by hand once E2E passes), an ingress controller.

## Choices worth knowing

- **The NSG is the firewall.** The cluster subnet may reach ports 8082, 8083, 8085 and 8093 on the on-premises subnet and nothing else. Without the deny rule, peering would open every port, Oracle included. For the link-down check, add a Deny rule above priority 100.
- **`snet-aks` has no NSG of its own.** AKS keeps one on the node interfaces and opens it for each LoadBalancer Service. A second one would block the public gateway.
- **The VM reaches the internet through a NAT gateway.** It has no public address, and a new subnet has no outbound route by default. It needs one to pull images.
- **Azure SQL uses a service endpoint, not a private endpoint.** Only `snet-aks` can connect. The schemas are therefore loaded from inside the cluster, not from a laptop.
- **Redis is reached privately by both sides.** ledger-service and account-service on the VM read the token blacklist and idempotency keys, so the private DNS zone is linked to the on-premises VNet as well.
- **Two addresses are fixed and repeated in `infra/k8s/config.yaml`:** the VM (`10.250.0.4`) and Kafka's internal load balancer (`10.240.15.200`). Change both places together.
- **Logging in to the VM.** There is no public address and SSH from the cluster is blocked by the NSG. Use `az vm run-command invoke`, or add a temporary NSG rule.

## Secrets

No secret is in this folder, and `main.bicepparam` holds none.

- The deployment takes one secret, the Azure SQL administrator password, from the environment of the shell that runs it. It stores it in Key Vault as `cloud-db-password`.
- The Redis key is written to Key Vault by the template as `redis-key`.
- The application secrets are generated and stored in Key Vault after the deployment. They do not pass through a template. The VM reads five of them by these names: `jwt-secret`, `oracle-password`, `app-user-password`, `audit-db-password`, `redis-key`. The cluster also needs `internal-service-secret` and the two inter-bank secrets.
- The values that were in `main.bicepparam` before 9 October 2026 are in the git history. They must never be used for anything deployed.

## Cost

List prices read from the Azure Retail Prices API on 8 October 2026 for Indonesia Central. They are not a quote, and the NAT gateway price was not read.

| Item | Price | Six days |
| --- | --- | --- |
| AKS node, `Standard_E2as_v5` | 0.122 an hour | 17.57 |
| VM, `Standard_B2as_v2` | 0.0855 an hour | 12.31 |
| Azure SQL S0, four databases | 0.4839 a day each | 11.61 |
| Azure Managed Redis B0 | 0.018 an hour | 2.59 |
| Container registry, Standard | 0.667 a day | 4.00 |
| Public IPs, disks, NAT gateway, logs | not all read | about 10 |
| **Total** | | **about USD 60** |

The trial has USD 200 of credit. Delete the resource group contents when the presentation is over.

## Checking it without creating anything

Compile (the pipeline's Validate stage runs the same command):

```
az bicep build --file infra/bicep/main.bicep
```

Ask Azure what a deployment would do. This checks quota, sizes and API versions against the real subscription and creates nothing:

```
az deployment group what-if --resource-group CooBS-rsg --parameters infra/bicep/main.bicepparam
```

## Deploying

Each command below creates or changes resources and costs money. Run them yourself, one at a time.

1. Set the three values in the shell (PowerShell shown). The password needs upper case, lower case, a digit and a symbol.

   ```
   $env:COOBS_SQL_ADMIN_PASSWORD = "<a new password, never committed>"
   $env:COOBS_VM_SSH_PUBLIC_KEY = Get-Content $HOME\.ssh\id_ed25519.pub
   $env:COOBS_KEYVAULT_ADMIN_OBJECT_ID = az ad signed-in-user show --query id -o tsv
   ```

2. Deploy. It takes roughly 15 to 25 minutes; Redis and AKS are the slow parts.

   ```
   az deployment group create --resource-group CooBS-rsg --name coobs-demo --parameters infra/bicep/main.bicepparam
   ```

3. Read the values `infra/k8s/config.yaml` needs.

   ```
   az deployment group show --resource-group CooBS-rsg --name coobs-demo --query properties.outputs
   ```

4. Start the on-premises zone, once the images are in the registry and the secrets are in Key Vault (next section).

If Azure refuses to create Azure Managed Redis on the trial, deploy with `deployManagedRedis=false` and use `infra/k8s/optional/redis-in-cluster.yaml`.

## The on-premises VM

`infra/onprem/docker-compose.yml` runs ledger, account, audit, batch, Oracle and the audit PostgreSQL on the VM. It reaches Kafka at the cluster's internal load balancer (`10.240.15.200:9094`) and Redis through the private endpoint.

The VM has no public address, so nothing is copied to it over SSH. `infra/scripts/deploy-onprem.ps1` packs the Compose file, `start.sh` and the Oracle and audit-store init scripts (about 30 KB), carries them inside an `az vm run-command` script, and runs `start.sh` on the VM. `start.sh` reads the secrets from Key Vault with the VM's own identity and writes them to `/opt/coobs/.env`; no secret passes through the laptop or the command line.

```
.\infra\scripts\deploy-onprem.ps1 -KeyVaultName <vault> -ImageTag <tag the pipeline pushed> -RedisHost <redis host name>
.\infra\scripts\deploy-onprem.ps1 -StatusOnly
```

Add `-SaveScriptTo <file>` to see exactly what would run on the VM without calling Azure.

Two things differ from the local stack on purpose. Each service gets `JAVA_OPTS=-Xmx384m`, because the images default to three quarters of the machine's memory per JVM. And batch-service is given the Oracle settings; locally it is given none and falls back to a password written in its code, which would not match a generated one.

## Removing it

Delete what the deployment created but keep the registry, or delete the whole resource group and lose the pushed images too. Key Vault keeps a deleted vault for 7 days under the same name; purge it before deploying again with `az keyvault purge`.

## Not verified

The what-if checks the templates, names, API versions and the Kubernetes version. It does not prove the points below.

- That a free trial may create Azure Managed Redis, and that `highAvailability: 'Disabled'` is accepted at B0.
- That the services connect to Azure SQL and Azure Managed Redis in Azure. Locally the five database services pass E2E on SQL Server 2022.
- That first boot installs Docker on the VM through the NAT gateway.
- `deploy-onprem.ps1` and `start.sh` on a real VM. Checked here: the generated script and archive are well formed, the shell syntax is valid, and the Compose file passes `docker compose config`.
- That 8 GB is enough for Oracle, PostgreSQL and four JVMs.
- The NAT gateway price.
