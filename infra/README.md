# Infrastructure: the demo deployment in Azure

**Area:** `/infra`. Dev 8 (Jared) leads; Dev 1 (Carl) supports.

**Status: compiles with Bicep 0.48.1 and passes Azure's what-if against the real subscription (9 October 2026: 33 resources to create, the registry untouched); never deployed.** Nothing in `infra/bicep` has created a resource yet. `infra/k8s` has its own README and status.

This is the in-depth architecture cut to what an Azure free trial allows. It is drawn in `docs/architecture-diagrams/demo-deployment.eraserdiagram`.

## What the free trial allows

Read from the subscription on 8 and 9 October 2026:

| Limit | Value | What it forces |
| --- | --- | --- |
| vCPUs in the region | 4 in total | One 2 vCPU AKS node and one 2 vCPU VM, with nothing to spare. No autoscaling, no node upgrade |
| Public IP addresses | 3 | Exactly three are used: AKS outbound, the one public entry (console and API), the on-premises NAT gateway |
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
| `frontdoor.bicep` | Front Door Standard with one endpoint and one route to the public address. **Off by default, and refused by Azure on a free trial**; see "Switching on HTTPS" |

Not created: Event Hubs (Kafka runs in the cluster), a VPN gateway (peering stands in for it), an ingress controller.

## Choices worth knowing

- **The NSG is the firewall.** The cluster subnet may reach ports 8082, 8083, 8085 and 8093 on the on-premises subnet and nothing else. Without the deny rule, peering would open every port, Oracle included. For the link-down check, add a Deny rule above priority 100.
- **`snet-aks` has no NSG of its own.** AKS keeps one on the node interfaces and opens it for each LoadBalancer Service. A second one would block the public entry.
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

2. Deploy. It takes roughly 15 to 25 minutes; Redis and AKS are the slow parts. The first line is needed once per subscription: the cluster's logging add-on needs that provider, what-if does not notice when it is missing, and the cluster then ends as `Failed` (seen on 10 October 2026; registering it and deploying again repaired it).

   ```
   az provider register --namespace Microsoft.OperationsManagement --wait
   az deployment group create --resource-group CooBS-rsg --name coobs-demo --parameters infra/bicep/main.bicepparam
   ```

3. Read the values `infra/k8s/config.yaml` needs.

   ```
   az deployment group show --resource-group CooBS-rsg --name coobs-demo --query properties.outputs
   ```

4. Publish the images and note the tag. There are two ways:

   - **From a machine with Docker**, while the pipeline is switched off. Sign in to the registry, then build and push the thirteen deployed services and the staff console from a clean checkout of the commit to deploy. `-DryRun` prints the commands without running them.

     ```
     az acr login --name CooBS
     ./infra/scripts/publish-images.ps1 -ImageTag <tag>
     ```

   - **From the pipeline**, which pushes them from a `release/*` branch once the `coobs-acr` service connection exists.

   Building inside the registry (`az acr build`) is not an option to plan on: the subscription is a free trial, and registry build tasks are not expected to be allowed on one. Not tried.

5. Deploy the cloud zone. This generates the application secrets into Key Vault, creates the Kubernetes Secret, loads the four databases with a Job in the cluster, gives the staff sign-ins a generated password in place of the seeded one, and applies `infra/k8s`. It prints the public address, how to read the staff password, and the exact command for step 6.

   ```
   ./infra/scripts/deploy-cloud.ps1 -ImageTag <tag>
   ```

   Add `-RenderOnly <file>` with `-SqlServerFqdn`, `-SqlAdminLogin` and `-RedisHost` to see the manifests it would apply without calling Azure or the cluster.

6. Start the on-premises zone (next section). It comes after step 5 because the VM reads secrets that step creates and connects to the Kafka broker that step starts.

7. Run the E2E suite against the public address. There is no separate Postman environment for Azure: the seed, and so the sign-in names, are the same as locally. Two things differ, the address and the staff password, and both are passed on the command line. `deploy-cloud.ps1` prints this command with the address filled in; read the password from Key Vault (`az keyvault secret show --vault-name <vault> --name staff-password --query value --output tsv`).

   ```
   npx --yes newman run postman/e2e/CooBS_End_To_End.postman_collection.json -e postman/CooBS_Local.postman_environment.json --env-var baseUrl=http://<public address> --env-var staffPassword=<staff password> --env-var tellerPassword=<staff password> --delay-request 150 --reporters cli
   ```

   One assertion depends on the network: step 1.1 expects the gateway's health answer within 500 ms. The scripts under `scripts/test_*.ps1` reach containers through `docker exec` and run against the local stack only.

If Azure refuses to create Azure Managed Redis on the trial, deploy with `deployManagedRedis=false` and use `infra/k8s/optional/redis-in-cluster.yaml`.

## The on-premises VM

`infra/onprem/docker-compose.yml` runs ledger, account, audit, batch, Oracle and the audit PostgreSQL on the VM. It reaches Kafka at the cluster's internal load balancer (`10.240.15.200:9094`) and Redis through the private endpoint.

The VM has no public address, so nothing is copied to it over SSH. `infra/scripts/deploy-onprem.ps1` packs the Compose file, `start.sh` and the Oracle and audit-store init scripts (about 30 KB), carries them inside an `az vm run-command` script, and runs `start.sh` on the VM. `start.sh` reads the secrets from Key Vault with the VM's own identity and writes them to `/opt/coobs/.env`; no secret passes through the laptop or the command line.

```
.\infra\scripts\deploy-onprem.ps1 -KeyVaultName <vault> -ImageTag <tag the pipeline pushed> -RedisHost <redis host name>
.\infra\scripts\deploy-onprem.ps1 -StatusOnly
```

Add `-SaveScriptTo <file>` to see exactly what would run on the VM without calling Azure.

One thing differs from the local stack on purpose. Each service gets `JAVA_OPTS=-Xmx384m`, because the images default to three quarters of the machine's memory per JVM.

## Switching on HTTPS

The first deployment answers on plain HTTP, so the address must not be given to anyone outside the team. There are two ways to put HTTPS in front of it. Do either once E2E has passed on the plain address.

### On a free trial: a TLS proxy in the cluster

Azure refuses Front Door on a free trial ("Free Trial and Student account is forbidden for Azure Frontdoor resources", 10 October 2026). `infra/k8s-https` does without it: one Caddy pod takes ports 80 and 443 of the same public address, fetches a certificate by itself, and passes everything on to the console's nginx. It costs nothing and needs no new address.

```
.\infra\scripts\deploy-cloud.ps1 -ImageTag <tag> -SkipSchemaLoad -DnsLabel <label> -AcmeEmail <an address you read>
```

- The address gets the host name `<label>.indonesiacentral.cloudapp.azure.com`. The label must be free in the region; the script's output is the HTTPS address.
- The script adds `https://<that name>` to the origins the gateway allows. Plain HTTP is answered with a redirect, and the bare IP address no longer serves anything.
- The certificate comes from Let's Encrypt, or from ZeroSSL when Let's Encrypt refuses. That can happen: names under `cloudapp.azure.com` share one weekly allowance with every Azure customer. The e-mail address is what makes the second issuer available.
- If the page does not open after two minutes, read `kubectl logs deployment/tls-proxy --namespace coobs --tail 40`.
- To go back to plain HTTP, run the script again without `-DnsLabel` and `-AcmeEmail`, then remove what is left: `kubectl delete deployment/tls-proxy service/frontend configmap/tls-proxy-config pvc/tls-proxy-data --namespace coobs`.

HTTPS ends at the proxy. Inside the cluster and across the link to the on-premises VM the calls are plain HTTP on private addresses, as before.

### On a paid subscription: Front Door

It cannot be in the first deployment, because its origin is the public address, which exists only after step 5. Not usable together with `-DnsLabel`.

1. Deploy the Bicep again with Front Door on and the address `deploy-cloud.ps1` printed. This adds five Front Door resources (the profile, an endpoint, a route, an origin group and an origin) and changes nothing else; run what-if first to see that. The address must be the real one: Azure refuses a documentation-range address such as 203.0.113.9 as an origin.

   ```
   $env:COOBS_FRONT_DOOR = "true"
   $env:COOBS_PUBLIC_ADDRESS = "<public address>"
   az deployment group create --resource-group CooBS-rsg --name coobs-demo --parameters infra/bicep/main.bicepparam
   ```

2. Run `deploy-cloud.ps1` again with the same image tag. It reads the Front Door host name from the outputs, adds `https://<that name>` to the origins the gateway allows, and closes the load balancer to everything except Front Door. It then prints the HTTPS address.
3. Use the HTTPS address everywhere: the console, `GATEWAY_URL` for the app, `baseUrl` for Postman. The plain address no longer answers.

To go back, open the load balancer again (`kubectl annotate service coobs-public -n coobs service.beta.kubernetes.io/azure-allowed-service-tags-`) and deploy the Bicep with `COOBS_FRONT_DOOR` unset or `false`; a normal deployment does not remove the Front Door resources, so delete the profile `afd-coobs-dev` by hand.

Front Door Standard has a base fee of about USD 35 a month before traffic. That figure is from memory, not read from the price API; check it before switching on. Behind Front Door every caller reaches the cluster from Front Door's addresses, which changes nothing for rate limits: calls without a token already share one bucket (`infra/k8s/README.md`).

## The mobile app

The Flutter app is not hosted anywhere: it is an Android package installed on the demo phones by hand. Build it against the gateway's address once `deploy-cloud.ps1` has printed it:

```
cd app
flutter build apk --release --dart-define=GATEWAY_URL=http://<public address>
```

The package is `app/build/app/outputs/flutter-apk/app-release.apk`. Three things to know:

- The address can also be changed on the phone, from the sign-in screen, so one package serves the local stack and Azure.
- The release build is signed with the debug key (`app/android/app/build.gradle.kts`). That is fine for installing by hand and not for a store.
- The Android manifest allows plain HTTP, and it has to while there is no HTTPS address. Once Front Door is on, build the app with the HTTPS address and have the mobile developers remove that allowance.

## The staff console

The console runs in the cluster as one nginx pod (`infra/k8s/frontend.yaml`) and is the public entry of the whole deployment: `http://<public address>` serves the console, and the same nginx passes `/api` and `/actuator` on to the gateway. So the console, the app, a partner bank and Postman all use that one address. This is how the console already works locally, where its nginx passes `/api` to the gateway container.

Admin and the tellers sign in with the generated staff password, not the seeded one. The sign-in page still pre-fills the seeded password, which is then wrong for staff: type the real one.

## Removing it

Delete what the deployment created but keep the registry, or delete the whole resource group and lose the pushed images too. Key Vault keeps a deleted vault for 7 days under the same name; purge it before deploying again with `az keyvault purge`.

## Not verified

The what-if checks the templates, names, API versions and the Kubernetes version. It does not prove the points below.

- That a free trial may create Azure Managed Redis, and that `highAvailability: 'Disabled'` is accepted at B0.
- That the services connect to Azure SQL and Azure Managed Redis in Azure. Locally the five database services pass E2E on SQL Server 2022.
- That first boot installs Docker on the VM through the NAT gateway.
- `deploy-onprem.ps1` and `start.sh` on a real VM. Checked here: the generated script and archive are well formed, the shell syntax is valid, and the Compose file passes `docker compose config`.
- That 8 GB is enough for Oracle, PostgreSQL and four JVMs.
- `deploy-cloud.ps1` against a real vault and cluster, and the schema Job against Azure SQL. Checked here: the render-only mode produces 10 Deployments (nine services and the console), Kafka and the on-premises endpoints with every placeholder filled, and the Job's shell script is valid. The same scripts load into SQL Server 2022 locally.
- The NAT gateway price.
- Front Door: the module compiles and passes what-if against the subscription, off (no Front Door resource) and on (five to create), and `Microsoft.Cdn` is registered (10 October 2026). **A free trial may not create it: Azure refused on 10 October 2026.** Whether `service.beta.kubernetes.io/azure-allowed-service-tags` closes the load balancer as intended is unproven.
- The TLS proxy (`infra/k8s-https`). Checked: the overlay renders, the cluster accepts it in a server-side dry run, and Caddy 2.10.0 parses the rendered configuration with both issuers. Never applied: that a certificate is issued for the Azure host name, and that the console and the app work through it, are unproven.
- The console's nginx pod on the cluster. It is the image the local stack runs, but here it runs without a mounted configuration and behind a load balancer.
- The staff password step of the schema job (`htpasswd` in an `httpd` image, then one `UPDATE`). The shell logic and the hash prefix are checked; the job has never run.
