# Infrastructure: the demo deployment in Azure

**Area:** `/infra`. Dev 8 (Jared) leads; Dev 1 (Carl) supports.

**Status: deployed on 10 October 2026 and proven by the E2E suite (143 of 143) over HTTPS at `https://coobs.indonesiacentral.cloudapp.azure.com`.** Everything in "What is created" exists except Front Door, which a free trial may not create. The section "Proven and not proven" at the end says what the deployment showed. The record of that deployment, with the commands as they were run, is `integrated-capstone-plans/AZURE_DEPLOYMENT_AS_BUILT.md`. `infra/k8s` has its own README.

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

## Updating a running deployment

`deploy-cloud.ps1` puts all ten cloud images on the one tag it is given, and the VM's Compose file does the same for its four. So the rule is: **one new tag, built for every image.**

### The normal way, also when one service changed

On the machine with Docker, from an up-to-date `dev`:

```
az acr login --name CooBS
.\infra\scripts\publish-images.ps1 -ImageTag <a tag not used before>
.\infra\scripts\deploy-cloud.ps1 -ImageTag <the same tag> -SkipSchemaLoad
```

- No `-Services` on the first line. Images whose code did not change come from Docker's cache, so only the changed ones are really rebuilt.
- The second line refuses to start if the registry lacks the tag for any image, keeps HTTPS on by itself, and fails if a rollout does not finish.
- `-SkipSchemaLoad` leaves the databases alone. They are loaded once, at the first deployment.

If one of ledger, account, audit or batch changed, also update the VM with the same tag:

```
.\infra\scripts\deploy-onprem.ps1 -KeyVaultName <vault> -ImageTag <the same tag> -RedisHost <redis host name>
```

### The shortcut: one cloud service only

```
.\infra\scripts\publish-images.ps1 -ImageTag <new tag> -Services transaction-service
kubectl set image deployment/transaction-service transaction-service=coobs-asfagkfjadgsgfcf.azurecr.io/coobs/transaction-service:<new tag> --namespace coobs
kubectl rollout status deployment/transaction-service --namespace coobs
```

Put the service's name in all four places; the container in each Deployment has the Deployment's name. This touches nothing else, HTTPS included.

It leaves that service on a tag the others do not have. Never give `deploy-cloud.ps1` a tag that was built for one service only: on 10 October 2026 that left nine services with a new pod waiting for a missing image, and, with the script as it was then, switched HTTPS off. The next full update builds everything under one tag again.
## Putting the data back to the seed

Testing changes the data: transfers, holds, balances, audit rows, notifications, end-of-day runs, the risk engine's daily counters. Two commands put all of it back to what the first deployment loaded. **Everything made since then is lost, on purpose.** Allow about fifteen minutes, during which the system is down.

Find the tag that is running, then reset the on-premises zone first (Oracle takes the longest), then the cloud zone:

```
kubectl get deployment api-gateway --namespace coobs --output "jsonpath={.spec.template.spec.containers[0].image}"
.\infra\scripts\deploy-onprem.ps1 -KeyVaultName <vault> -ImageTag <the tag the VM runs> -RedisHost <redis host name> -ResetData
.\infra\scripts\deploy-onprem.ps1 -StatusOnly
.\infra\scripts\deploy-cloud.ps1 -ImageTag <the tag the cluster runs> -ResetData
```

Run the third line until all six containers are healthy before the fourth. Then run the E2E suite once to see that the seed is back.

| What | How it is reset |
| --- | --- |
| Oracle and the audit store on the VM | `deploy-onprem.ps1 -ResetData` removes both data volumes, so the init scripts load the seed again when the containers start |
| The four Azure SQL databases | `deploy-cloud.ps1 -ResetData` stops the services, then the load job removes every table and loads the seed again |
| Redis | The same job empties it first: risk counters, idempotency keys, sessions. Everyone signs in again |
| Staff password | Unchanged: the job sets the one from Key Vault on every run |
| HTTPS and the certificate | Untouched |
| Kafka | Not emptied. The old events stay in the topics and have already been consumed, so nothing is replayed |

Both zones must be reset together. The ledger on the VM and the transfer records in the cloud describe the same transfers; resetting one side alone leaves them disagreeing.

If the cloud command stops half way, the services stay stopped. Run it again with `-ResetData`; it starts from an empty state each time.

On the local stack the same is done by removing the volumes (`docker compose down -v`) and starting again. `scripts/reset_demo_data.ps1` is smaller: it clears the end-of-day state and the risk counters only.
## Monitoring

Two things watch the deployment. The first came with the Bicep; the second is optional and is switched on by putting one secret in Key Vault.

| What | Covers | Gives |
| --- | --- | --- |
| Container Insights into the Log Analytics workspace | The cluster only | Container logs (table `ContainerLog`), pod and node health. Capped at 1 GB a day |
| Datadog agents (`infra/k8s-monitoring`, `infra/onprem/datadog`) | The cluster and the VM, on one screen | Logs of every service; CPU, memory and restarts of every container and of both hosts; and the metrics each service already publishes at `/actuator/prometheus`: request counts and times per endpoint, JVM memory, threads, database pool, Kafka listeners |

### Why Datadog is set up the way it is

The node has about 130m of CPU left to reserve. On 11 October 2026 Azure's managed Prometheus and application monitoring were switched on, reserved 766m between them, and pushed six services off the node. So the cluster gets exactly one agent pod that reserves 50m, with the chart's operator, cluster agent, trace agent, process agent and system probe all switched off (`infra/k8s-monitoring/datadog-values.yaml`). The VM, which has memory to spare, gets one more container.

No service is changed. The agents read what the services already write and publish.

### Switching it on

1. Create a Datadog account and note its site, the host name in the address bar after signing in without the `app.` in front: `datadoghq.com`, `us5.datadoghq.com`, `datadoghq.eu` and so on. Create an API key under Organization Settings.
2. Put both in Key Vault. The key goes in from a file so that it is not left in the shell's history; delete the file afterwards.

   ```
   az keyvault secret set --vault-name <vault> --name datadog-api-key --file <a file holding only the key> --output none
   az keyvault secret set --vault-name <vault> --name datadog-site --value <the site> --output none
   ```

3. Run the two deploy scripts as for any update. Each starts its agent when it finds the key, and says so.

   ```
   .\infra\scripts\deploy-cloud.ps1 -ImageTag <the tag the cluster runs> -SkipSchemaLoad
   .\infra\scripts\deploy-onprem.ps1 -KeyVaultName <vault> -ImageTag <the tag the VM runs> -RedisHost <redis host name>
   ```

4. In Datadog, within a few minutes: two hosts under Infrastructure (the node and `vm-coobs-onprem`); logs with `kube_namespace:coobs` or `host:vm-coobs-onprem`; metrics that begin `coobs.`, for example `coobs.http_server_requests_seconds.count` split by `application`, `uri` and `status`.

Without the secret `datadog-api-key` both scripts skip this and behave as before.

### Setting Datadog up by script

The agents send the data; this makes Datadog read and show it, without clicking through its screens. It needs one more secret, an Application key (Datadog, Organization Settings, Application Keys), stored in Key Vault as `datadog-app-key` the same way as the API key.

```
.\infra\scripts\setup-datadog.ps1 -DryRun
.\infra\scripts\setup-datadog.ps1 -KeyVaultName <vault>
```

The first line only prints what would be created. The second creates, through Datadog's API: the two log pipelines described under "Making log severities right", the dashboard from `datadog-dashboard.json`, and the four monitors in the table below. Anything that already exists under the same name is left alone, so it can be run again. It changes the Datadog account and nothing in Azure. The sections that follow say how to do each part by hand instead.
### The dashboard

Datadog does not make a dashboard for the `coobs.` metrics by itself. One is in `infra/k8s-monitoring/datadog-dashboard.json`: requests, response times and errors per service, the slowest and busiest endpoints, gateway traffic by route, JVM memory, database connections, container CPU and memory in both zones, restarts, and the latest error lines. To load it: Dashboards, New Dashboard, give it any name, then the gear at the top right, **Import dashboard JSON**, and paste the file's contents.

Two things in Datadog will look incomplete, and both are expected:

- **The Kubernetes pages** (Infrastructure, Kubernetes) report the cluster as misconfigured, with no Cluster Agent and the orchestrator "partial". That is the part left out to fit the node. Containers, hosts, logs and metrics do not need it.
- **`mobile-bff` has no `coobs.` metrics.** It answers `/actuator/prometheus` only to an admin token, so it is not in the agent's list. Its logs and container figures are collected, and its calls show at the gateway by route.
### Alerts to create in Datadog

They are made in Datadog's own screens, not from this repository. Four cover the cases that matter:

| Monitor | On |
| --- | --- |
| A host stopped reporting | Either host silent for five minutes |
| A container keeps restarting | Restart count of any container in `kube_namespace:coobs` rising |
| Errors at the gateway | `coobs.http_server_requests_seconds.count` with `status:5*` above a few a minute |
| Error lines logged by a service | `coobs.logback_events.count` with `level:error` above zero, by `application`. A metric, so it is right without any log parsing |

### Making log severities right

Out of the box Datadog guesses a log line's severity from the stream it was written to: everything on stderr is shown as an error, everything on stdout as information. That is wrong in both directions here. PostgreSQL and Oracle write routine messages to stderr, so a checkpoint line appears as an error; the Java services write everything to stdout, so a real `ERROR` line appears as information. Until this is set, do not build a monitor on `status:error`; use the metric in the table above.

It is set once, in Datadog under Logs, Pipelines:

1. **PostgreSQL:** add the PostgreSQL pipeline from the integration library. It matches `source:postgres` and reads the severity from the line (`LOG`, `WARNING`, `ERROR`).
2. **The services:** add a pipeline with the filter `(kube_namespace:coobs OR host:vm-coobs-onprem) -source:postgres`. Give it a Grok parser with the rule below, then a Status remapper on the attribute `level`.

   ```
   coobs_spring %{notSpace:log_time}\s+%{word:level}\s+%{data:rest}
   ```

   A service's line looks like `2026-10-11T03:52:34.977Z  INFO 1 --- [transaction-service] [nio-8090-exec-5] c.g.f.t.c.HistoryInternalController : ...`, so the second word is the severity. Lines that do not start that way, a stack trace for one, are left as they are.
### Tracing a service

Tracing follows one request through a service with timings: the request itself, each call the service makes to another service, each database query, each Kafka message. It is opt-in, per service, and needs no change to an image:

```
.\infra\scripts\deploy-cloud.ps1 -ImageTag <the tag the cluster runs> -SkipSchemaLoad -TraceServices transaction-service,orchestration-service
```

The script adds Datadog's Java tracer to each named Deployment (`infra/k8s-monitoring/java-tracer-patch.yaml`), restarts it, and waits for it, one service at a time. The traces appear in Datadog under APM, by service name. Once added, the tracer stays through later deployments without the option.

- Trace a few services, not all nine. Each traced JVM starts more slowly and uses more memory, inside the same 256 MB heap and 640 MB limit as before; on 11 October two services used 47 and 61 MB of that heap. Watch a traced service's restart count for the first hour.
- `transaction-service` and `orchestration-service` together cover a transfer from intake through the saga, including the timed calls to the risk engine and the ledger.
- A request is followed across two services only when both are traced. The four services on the VM are not covered by this option.
- To take the tracer off one service: `kubectl rollout undo deployment/<service> --namespace coobs` straight after adding it, or delete the Deployment and run the deploy script again.

### What this does not give

- **Azure's managed Prometheus and Application Insights.** Both are off, for the reason above.
- **Datadog's Kubernetes pages.** They need the Cluster Agent, which is left out to fit the node.
### Switching it off

Delete the two secrets from Key Vault, then remove the agents: `kubectl delete -f infra/k8s-monitoring/datadog-agent.yaml` on the cluster, and on the VM `docker rm -f datadog-agent`, run through `az vm run-command invoke`.

### To know before switching it on

- Logs and metrics leave Azure for Datadog's servers. The data is seeded demo data.
- A Datadog trial lasts two weeks; after it a plan is needed. Log volume and the number of `coobs.` metrics are what a plan charges for, which is why system pods' logs are left out and the metric list is short.
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
- Once HTTPS is on, a later run of the script keeps it without being told; it reads the label and the address from the cluster. To go back to plain HTTP, run the script with `-PlainHttp`, then remove what is left: `kubectl delete deployment/tls-proxy service/frontend configmap/tls-proxy-config pvc/tls-proxy-data --namespace coobs`.

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

## Proven and not proven

Proven by the deployment of 10 October 2026 (free trial, Indonesia Central) and an E2E run of 143 of 143 against the HTTPS address:

- The Bicep creates everything except Front Door. A free trial may create Azure Managed Redis at B0.
- The services connect to Azure SQL and Azure Managed Redis, and the schema job loads the four databases and sets the staff password.
- First boot installs Docker on the VM through the NAT gateway, and `deploy-onprem.ps1` with `start.sh` starts the on-premises zone on it.
- `deploy-cloud.ps1` against the real vault and cluster, including the console's nginx pod as the public entry.
- The TLS proxy: Let's Encrypt issued a certificate for the Azure host name on the first attempt, plain HTTP is redirected, and the console, the app and the E2E suite work through it.
- The Datadog agents and the Java tracer (11 October 2026): the agent pod runs on the node beside every service, logs and the `coobs.` metrics are sent from both zones, and traces arrive from all nine cluster services, none of which restarted.

Three things the deployment taught, all now in this folder:

- The provider `Microsoft.OperationsManagement` must be registered before the cluster is created (step 2).
- The node gives 1900m of CPU to reserve and AKS's own pods take about 1160m, so each service reserves 50m. At 100m four pods found no room.
- Each service has a CORS list of its own, and the on-premises ones refused browser calls from the public address. The gateway now judges the origin once and does not pass it on.

Not proven:

- Front Door. Azure refuses it on a free trial, so the module has never created anything, and whether `service.beta.kubernetes.io/azure-allowed-service-tags` closes the load balancer as intended is unknown.
- The link-down check (a Deny rule on the NSG) against this deployment.
- Load: nothing heavier than the E2E suite has run. Eight GB on the VM and two vCPUs on the node were enough for that and no more is known.
- Certificate renewal, which the proxy does by itself after about two months.
- The NAT gateway price.
