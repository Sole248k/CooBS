# Enterprise-style deployment: two clusters and Front Door

**Area:** `/infra/enterprise`. A second, separate deployment of the same system, closer to the enterprise target. It lives in its own resource group, `CooBS-ent-rsg`, with its own registry, Key Vault, databases and VM. **The demo deployment in `CooBS-rsg` is not touched by anything here and stays the fallback.**

**Status: written on 13 October 2026 and never deployed.** Checked without Azure only: the Bicep compiles, both Kubernetes overlays render, the deploy script parses and renders offline. Everything under "Not proven" is unknown until the first run.

## What it is

| Part | Where | What runs there |
| --- | --- | --- |
| Edge | Azure Front Door Standard | HTTPS, the one public name. Switched on in a second pass |
| Channels cluster | `aks-coobs-channels`, one node | The staff console, `api-gateway`, `mobile-bff`, `staff-bff`. The only public address |
| Services cluster | `aks-coobs-services`, one node | `auth`, `customer`, `transaction`, `orchestration`, `risk-engine`, `notification`, `order`, and Kafka. No public address |
| Data | Azure SQL (five databases), Azure Managed Redis | As in the demo deployment |
| On-premises zone | `vm-coobs-onprem` in its own VNet | `ledger`, `account`, `audit`, `batch`, Oracle, the audit store |

How the clusters talk:

- The channels cluster keeps the Service names `auth-service`, `customer-service`, `transaction-service`, `notification-service` and `order-service`, with no pods behind them. Each points at a fixed private address (`10.240.15.211` to `.215`) on the services cluster's internal load balancer. The gateway and the BFFs therefore call `http://auth-service:8081` exactly as they do in one cluster, and no image changes.
- `orchestration-service` and `risk-engine` have no address outside the services cluster. Nothing in the channels cluster can reach them.
- Both clusters reach the on-premises VM the same way as before, through the firewall's four ports.
- Both clusters take pod addresses from the one AKS subnet. That is what lets the existing network, SQL rule and firewall modules be used unchanged. A production layout would give each cluster its own subnet and its own firewall rules.

Files:

| File | Purpose |
| --- | --- |
| `main.bicep`, `main.bicepparam` | The demo template with two AKS clusters. Uses the modules in `infra/bicep/modules` |
| `provision.ps1` | Creates the resource group and registry, then deploys the Bicep |
| `k8s/services`, `k8s/channels` | Kustomize overlays over `infra/k8s`: each removes what the other cluster runs |
| `deploy-enterprise.ps1` | Secrets, schema load, and both overlays, services cluster first |

## Cost

List prices from the Azure Retail Prices API (machines, SQL and Redis read on 8 October 2026; Front Door read on 13 October). The registry and the last row were not read.

| Item | A day (USD) |
| --- | --- |
| Two AKS nodes, `Standard_E2as_v4`, at 0.136 an hour | 6.53 |
| VM, `Standard_D2as_v4`, at 0.108 an hour | 2.59 |
| Azure SQL, five S0 databases | 2.42 |
| Azure Managed Redis B0 | 0.43 |
| Front Door Standard, 35 a month | 1.17 |
| Container registry, Basic | about 0.17 |
| NAT gateway, public addresses, load balancers, disks, logs | about 2.50 |
| **Total** | **about 15.80** |

This is in addition to the demo deployment's 10.50 a day, so about USD 25 a day while both exist. It uses 6 vCPUs; the region allows 10 and the demo deployment holds 4, so the quota is used in full.

## Deploying

Every step creates or changes resources. Run them yourself, in order. Steps 1 and 5 need only the Azure CLI; step 2 needs Docker.

1. **Check, then create.** The first line shows what would be created; the second creates it (20 to 30 minutes). Both ask for a new SQL administrator password.

   ```
   .\infra\enterprise\provision.ps1 -RegistryName <new registry name> -WhatIf
   .\infra\enterprise\provision.ps1 -RegistryName <new registry name>
   ```

2. **Publish the images** to the new registry, from the machine with Docker.

   ```
   az acr login --name <new registry name>
   az acr show --name <new registry name> --resource-group CooBS-ent-rsg --query loginServer --output tsv
   .\infra\scripts\publish-images.ps1 -ImageTag ent-1 -Registry <the login server the second line printed>
   ```

   The login server is not `<name>.azurecr.io` on this subscription: Azure adds a suffix, as in `ecoobs-aefbgjeyahddhxdt.azurecr.io`. Pushing to the short name fails with a host name lookup error.

3. **Deploy both clusters.** It prints the public address and the exact commands for steps 4 to 6.

   ```
   .\infra\enterprise\deploy-enterprise.ps1 -ImageTag ent-1 -RegistryName <new registry name>
   ```

4. **Start the on-premises zone**, with the command step 3 printed. It is `infra/scripts/deploy-onprem.ps1` with `-ResourceGroup CooBS-ent-rsg` and the new registry.

5. **Front Door.** Deploy the Bicep again with the public address, then run the deploy script again so the gateway allows the HTTPS origin and the load balancer closes to everything but Front Door. A new Front Door endpoint can take some minutes before it answers.

   ```
   .\infra\enterprise\provision.ps1 -RegistryName <new registry name> -FrontDoorAddress <public address>
   .\infra\enterprise\deploy-enterprise.ps1 -ImageTag ent-1 -RegistryName <new registry name> -SkipSchemaLoad
   ```

6. **Run the E2E suite** against the address step 5 printed.

To see what would be applied without Azure or a cluster:

```
.\infra\enterprise\deploy-enterprise.ps1 -ImageTag t -RegistryName r -RenderOnly out -SqlServerFqdn s.database.windows.net -SqlAdminLogin coobsadmin -RedisHost r.redis.azure.net
```

## Monitoring

Two things watch this deployment.

| What | Covers | State |
| --- | --- | --- |
| Container Insights into the Log Analytics workspace | Both clusters: container logs, pod and node health | On from the first deployment; the Bicep sets it. Portal, each cluster, Monitoring, Insights or Logs |
| Datadog agents | Both clusters and the VM: logs, CPU and memory, and each service's request counts, response times and JVM figures | Off until this deployment's Key Vault holds the key |

To switch Datadog on, put the key and the site into the vault of this deployment, then run the two deploy scripts again. The key goes in from a file so it is not left in the shell's history; delete the file afterwards. The vault's name is in the output of `provision.ps1`.

```
az keyvault secret set --vault-name <vault> --name datadog-api-key --file <a file holding only the key> --output none
az keyvault secret set --vault-name <vault> --name datadog-site --value <the site, for example ap1.datadoghq.com> --output none
.\infra\enterprise\deploy-enterprise.ps1 -ImageTag ent-1 -RegistryName <new registry name> -SkipSchemaLoad
```

Then run the `deploy-onprem.ps1` line again for the VM's agent. In Datadog the two clusters appear as `aks-coobs-services` and `aks-coobs-channels` with `env:enterprise`, apart from the demo deployment's `env:demo`.

- The VM reports under the host name `vm-coobs-onprem` and `env:demo`, the same as the demo deployment's VM, because the Compose file is shared. With both deployments running, Datadog shows the two VMs as one host.
- **One command does all of it** when the demo deployment's vault already holds the Datadog secrets: `deploy-enterprise.ps1 ... -SkipSchemaLoad -DatadogFromVault <demo vault> -TraceServices all` copies the secrets, starts the agents and adds the tracer to every Java service, one at a time. Name services in place of `all` to trace only some.
- **Traces** appear in Datadog under APM with `env:enterprise`. A request is followed across two services only when both are traced; the four services on the VM are not.
- **Dashboard and monitors** are created once per Datadog account by `infra/scripts/setup-datadog.ps1 -KeyVaultName <vault>`, which needs `datadog-app-key`. The dashboard reads the `coobs.` metrics of every host that reports, so it shows this deployment and the demo one together.

## Removing it

One command ends every charge of this deployment and leaves the demo deployment as it is.

```
az group delete --name CooBS-ent-rsg --yes
```

The Key Vault stays soft-deleted for 7 days at no cost. To deploy again under the same names before then, purge it with `az keyvault purge --name <vault>`.

## Not proven

- **Any of it in Azure.** Nothing here has been deployed.
- **Two AKS clusters in one subnet**, each with a role on the same virtual network.
- **Five fixed private addresses on one internal load balancer**, and whether the channels cluster reaches them. If a Service stays without an address, `kubectl --context aks-coobs-services describe service <name> --namespace coobs` says why.
- **Front Door.** The module has never created anything, on either deployment.
- **`order-service` on SQL Server**, and capturing a shop order into account 9003 (`infra/README.md`).
- **The quota at create time.** The demo deployment's sizes (`Standard_E2as_v5`, `Standard_B2as_v2`) cannot be used here: on pay-as-you-go their families have a quota of 0 in this region, and the first check on 13 October was refused for it. This deployment uses `Standard_E2as_v4` and `Standard_D2as_v4`, whose families allow 10 vCPUs each, and the same check then passed with 39 resources to create. The regional limit of 10 leaves exactly the 6 vCPUs this needs.

## Not in this folder

- **Event Hubs in place of Kafka.** Kafka still runs as one broker in the services cluster. Event Hubs needs two things first: six services build their Kafka settings in code with no way to pass credentials (`account`, `batch`, `ledger`, `notification`, `orchestration`, `transaction`), and the system uses 20 topics where Event Hubs Standard allows 10 in a namespace, so it needs the Premium tier at USD 1.2024 an hour, about 29 a day (read from the price API on 13 October 2026).
- The Java tracer, the data reset and the in-cluster TLS proxy. Use the demo deployment's scripts as the model if they are wanted here.
