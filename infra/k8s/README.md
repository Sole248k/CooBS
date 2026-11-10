# Kubernetes manifests: the cloud zone

These manifests run the ten cloud services, the staff console and Kafka on one AKS cluster, in the `coobs` namespace. The four on-premises services (ledger, account, audit, batch) are not here: they run on the on-premises VM, and the cluster reaches them through `onprem-services.yaml`.

**Status: applied to the cluster on 10 October 2026 by `infra/scripts/deploy-cloud.ps1`, together with the HTTPS overlay in `infra/k8s-https`; the E2E suite passed against it.** What that proved and what it did not is at the end of `infra/README.md`.

## Files

| File | Holds |
| --- | --- |
| `kustomization.yaml` | The list of what is applied, the registry and image tag, and the two address substitutions |
| `namespace.yaml` | The `coobs` namespace |
| `config.yaml` | `coobs-config`: every non-secret setting, including the values to replace |
| `<service>.yaml` (nine) | A Deployment and a ClusterIP Service each |
| `frontend.yaml` | The staff console (nginx) and `coobs-public`, the one public load balancer. nginx serves the console and passes `/api` and `/actuator` to the gateway, so the console, the app, a partner bank and Postman all use one address |
| `onprem-services.yaml` | Services with no selector, pointing at the on-premises VM |
| `kafka.yaml` | One Kafka broker, its internal load balancer for the VM, and the job that creates the topics |
| `secret.template.yaml` | The keys the Secret must have. Not applied |
| `optional/redis-in-cluster.yaml` | Fallback Redis. Not applied |
| `jobs/load-schemas.yaml` | A Job that loads the four Azure SQL databases from `database/azure-sql/`. Applied by the deploy script, not by `kustomization.yaml` |
| `ingress-tls.yaml` | Target-state Ingress and Key Vault CSI class from FIN-807. Not applied; see the last section |

## Before applying

`infra/scripts/deploy-cloud.ps1` does every step in this section and the next: it reads the values from the Bicep outputs, generates the application secrets into Key Vault, creates the Secret, loads the databases with `jobs/load-schemas.yaml`, and applies the rendered manifests. It edits no file in this folder. The steps below are what it does, for doing them by hand.

1. In `config.yaml`, replace the five values marked REPLACE:

   | Key | Value |
   | --- | --- |
   | `ONPREM_VM_IP` | Private address of the on-premises VM |
   | `KAFKA_EXTERNAL_IP` | A free address in the AKS subnet, for Kafka's internal load balancer |
   | `SQL_SERVER_FQDN`, `CLOUD_DB_USER` | The Azure SQL logical server and its login |
   | `REDIS_HOST` | The Azure Managed Redis host name |

2. In `kustomization.yaml`, set every `newTag` to the tag the pipeline pushed.
3. Create the Secret `coobs-app-secrets` in the `coobs` namespace with the seven keys listed in `secret.template.yaml`. The values come from Key Vault at deploy time; they are never written to a file in this repository. Create the namespace first:

   ```
   kubectl apply -f infra/k8s/namespace.yaml
   ```

4. On the on-premises VM, point the services at the cluster: `KAFKA_BOOTSTRAP_SERVERS=<KAFKA_EXTERNAL_IP>:9094`.

## Applying

```
kubectl kustomize infra/k8s
kubectl apply -k infra/k8s
kubectl -n coobs get pods
kubectl -n coobs get service coobs-public
```

The first command renders everything locally and needs no cluster. The last one shows the public address once Azure has assigned it. On port 80 it serves the console, and the API under `/api`.

All nine JVMs start together on two vCPUs, so the first start is slow. Each pod is allowed six minutes before it is restarted.

## Choices that differ from Compose

- **Database.** The manifests give each service an Azure SQL connection through Spring's own variables (`SPRING_DATASOURCE_URL` and the driver and dialect names), which override the PostgreSQL lines in `application.properties`. The images must contain the SQL Server JDBC driver, and the schemas must be loaded, before any of the five database services will start.
- **Redis.** Azure Managed Redis, with TLS and an access key supplied as `SPRING_DATA_REDIS_SSL_ENABLED` and `SPRING_DATA_REDIS_PASSWORD`.
- **Heap.** `JAVA_OPTS` is `-Xmx256m` for all nine. Compose sets that for most of them; the gateway, auth and notification use a percentage of memory there.
- **Health.** Probes use `/actuator/health/liveness` and `/actuator/health/readiness`, not `/actuator/health`, so a Redis or database outage does not restart every pod.
- **One public address.** Locally the gateway is published on 8080 and the console on 3000. Here only the console's nginx is public, and every API call passes through it. The gateway then sees all calls arrive from that pod: rate limits per signed-in user are unchanged, but calls with no token (sign-in above all) share one bucket of 10 a second for everybody.
- **Token life.** `JWT_EXPIRATION_MS` is two hours, not the local 24, because neither client can refresh a token yet.
- **Staff password.** The schema job replaces the seeded staff password hashes with the hash of `staff-password` from Key Vault. Members keep the seeded demo password.

## Not verified

- Whether the five database services start against Azure SQL. No service has been run on SQL Server yet.
- Whether the services connect to Azure Managed Redis with the two Spring variables, and whether a free trial can create that service.
- The Kafka broker's two listeners, in particular the on-premises VM producing through the internal load balancer.
- Whether the `apache/kafka` image runs as user 1000, which `fsGroup: 1000` in `kafka.yaml` assumes for the data disk.
- Memory: nine pods at a 640Mi limit plus Kafka at 1Gi is about 7 GB on a 16 GB node. Not measured.
- The gateway takes its CORS origins from `CORS_ALLOWED_ORIGINS` in `config.yaml`. A browser client served from an origin that is not listed there is refused, so add the console's address when it has one.

## `ingress-tls.yaml`

It is kept for the target state and is not in `kustomization.yaml`. To use it, the cluster needs an ingress controller, a certificate issuer and the Key Vault CSI add-on, none of which the Bicep installs. It also places its objects in the `default` namespace and names the vault `kv-coobs-dev`; both need changing to match this folder and the Bicep.
