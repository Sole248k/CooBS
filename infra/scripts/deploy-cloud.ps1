<#
.SYNOPSIS
    Deploys the cloud zone to the AKS cluster the Bicep created.
.DESCRIPTION
    Run after `az deployment group create` and after the images are in the registry. In order:

      1. Reads the Bicep outputs (cluster, Key Vault, Azure SQL, Redis).
      2. Generates the application secrets into Key Vault. A secret that already exists is kept.
      3. Creates the Kubernetes Secret coobs-app-secrets from Key Vault.
      4. Loads the four databases from database/azure-sql/ with a Job in the cluster, and
         gives the staff sign-ins (admin and the tellers) the password it generated in step 2,
         in place of the seeded one that is written in this repository.
      5. Renders infra/k8s with the real values and applies it.

    No secret is written to the repository or printed. Each one is held in a temporary file
    for the moment kubectl reads it, and deleted.

    It changes Key Vault and the cluster, so run it yourself. It needs the Azure CLI (signed
    in, with the Key Vault Secrets Officer role the Bicep gives) and kubectl.

    Run infra/scripts/deploy-onprem.ps1 afterwards: the VM reads secrets this script creates
    and connects to the Kafka broker this script starts.
.EXAMPLE
    .\infra\scripts\deploy-cloud.ps1 -ImageTag 20261010.1
.EXAMPLE
    .\infra\scripts\deploy-cloud.ps1 -ImageTag 20261010.1 -SkipSchemaLoad -DnsLabel coobs-demo -AcmeEmail someone@example.com
.EXAMPLE
    .\infra\scripts\deploy-cloud.ps1 -ImageTag t -RenderOnly rendered.yaml -SqlServerFqdn s.database.windows.net -SqlAdminLogin coobsadmin -RedisHost r.redis.azure.net
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$ImageTag,
    [string]$ResourceGroup = "CooBS-rsg",
    [string]$DeploymentName = "coobs-demo",
    [switch]$SkipSchemaLoad,
    # Puts the cloud data back to the seed: stops the services, empties Redis, removes every table
    # from the four databases and loads them again. Everything made since the first load is lost.
    # The on-premises data is reset by deploy-onprem.ps1 -ResetData; do both, that one first.
    [switch]$ResetData,
    # HTTPS at the public entry without Front Door (infra/k8s-https): the address gets the host
    # name <label>.<location>.cloudapp.azure.com and a TLS proxy that fetches a certificate for it.
    [ValidatePattern('^[a-z][a-z0-9-]{1,61}[a-z0-9]$')] [string]$DnsLabel,
    # The certificate issuer's contact address. Needed with -DnsLabel.
    [string]$AcmeEmail,
    [string]$Location = "indonesiacentral",
    # Once HTTPS is on, a later run keeps it without being told. This switch turns it off.
    [switch]$PlainHttp,
    # Services to add Datadog's Java tracer to (infra/k8s-monitoring/java-tracer-patch.yaml).
    # Needs the Datadog key in Key Vault. Once added it stays; naming a service again is harmless.
    [ValidatePattern('^[a-z][a-z0-9-]+$')] [string[]]$TraceServices = @(),
    # The registry the images are pulled from, by its Azure resource name.
    [string]$RegistryName = "CooBS",
    # Writes the manifests that would be applied to this file and stops, without calling Azure or the cluster.
    # Needs the three values below, which otherwise come from the Bicep outputs.
    [string]$RenderOnly,
    [string]$SqlServerFqdn,
    [string]$SqlAdminLogin,
    [string]$RedisHost,
    # With -RenderOnly: render as if Front Door were on with this host name. Otherwise it is read from the Bicep outputs.
    [string]$FrontDoorHost
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$namespace = "coobs"
$noBom = New-Object System.Text.UTF8Encoding $false
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("coobs-cloud-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
New-Item -ItemType Directory -Path $work | Out-Null

function Invoke-Native([string]$what, [scriptblock]$command) {
    $result = & $command
    if ($LASTEXITCODE -ne 0) { throw "$what failed (exit $LASTEXITCODE)" }
    return $result
}

# True when the command succeeds. Its error text is dropped: "not found" is an expected answer here.
function Test-Native([scriptblock]$command) {
    $ErrorActionPreference = "Continue"
    & $command 2>$null | Out-Null
    return ($LASTEXITCODE -eq 0)
}

function New-Hex([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    return -join ($buffer | ForEach-Object { $_.ToString("X2") })
}

# Renders infra/k8s and puts the deployment's values in place of the placeholders.
function Get-Manifests([string]$sqlFqdn, [string]$sqlLogin, [string]$redis, [string]$frontDoor) {
    if ($DnsLabel -and $frontDoor) { throw "-DnsLabel puts HTTPS at the cluster's own entry; Front Door is on in this deployment. Use one or the other." }
    if ($DnsLabel -and -not $AcmeEmail) { throw "-DnsLabel needs -AcmeEmail" }
    if ($ResetData -and $SkipSchemaLoad) { throw "-ResetData loads the databases again, so it cannot be used with -SkipSchemaLoad" }
    $folder = if ($DnsLabel) { "infra\k8s-https" } else { "infra\k8s" }
    $rendered = Join-Path $work "rendered.yaml"
    Invoke-Native "kubectl kustomize" { kubectl kustomize (Join-Path $repo $folder) --output $rendered } | Out-Null
    $text = [System.IO.File]::ReadAllText($rendered)
    $text = $text.Replace("REPLACE-server-name.database.windows.net", $sqlFqdn)
    $text = $text.Replace("REPLACE-sql-admin-login", $sqlLogin)
    $text = $text.Replace("REPLACE-cache-name.indonesiacentral.redis.azure.net", $redis)
    $text = $text.Replace(":CHANGE_ME", ":$ImageTag")
    $httpsHost = $frontDoor
    if ($DnsLabel) {
        $httpsHost = "$DnsLabel.$Location.cloudapp.azure.com"
        $text = $text.Replace("REPLACE-dns-label", $DnsLabel).Replace("REPLACE-public-host", $httpsHost).Replace("REPLACE-acme-email", $AcmeEmail)
    }
    if ($httpsHost) {
        # Behind HTTPS the browser's origin is the HTTPS host name, which the gateway must allow.
        $listed = "http://localhost:3000,http://localhost:5173"
        if (-not $text.Contains($listed)) { throw "CORS_ALLOWED_ORIGINS in infra/k8s/config.yaml is no longer the list this script adds the HTTPS origin to" }
        $text = $text.Replace($listed, "$listed,https://$httpsHost")
    }
    if ($text -match "REPLACE-|CHANGE_ME") { throw "A placeholder is still in the rendered manifests: $($Matches[0])" }
    return $text
}

try {
    if ($RenderOnly) {
        foreach ($name in "SqlServerFqdn", "SqlAdminLogin", "RedisHost") {
            if (-not (Get-Variable $name -ValueOnly)) { throw "-RenderOnly needs -$name" }
        }
        [System.IO.File]::WriteAllText($RenderOnly, (Get-Manifests $SqlServerFqdn $SqlAdminLogin $RedisHost $FrontDoorHost), $noBom)
        Write-Host "Rendered the manifests to $RenderOnly. Nothing was sent to Azure or the cluster."
        return
    }

    # -------------------------------------------------------------------------
    Write-Host "1. Reading the outputs of deployment $DeploymentName" -ForegroundColor Cyan
    $json = Invoke-Native "az deployment group show" {
        az deployment group show --resource-group $ResourceGroup --name $DeploymentName --query properties.outputs --output json
    }
    $outputs = ($json -join "`n") | ConvertFrom-Json
    $cluster = $outputs.aksClusterName.value
    $vault = $outputs.keyVaultName.value
    $sqlFqdn = $outputs.sqlServerFqdn.value
    $sqlLogin = $outputs.sqlAdminLogin.value
    $redis = $outputs.redisHostName.value
    # Empty until the Bicep is deployed again with Front Door switched on (infra/README.md).
    $frontDoor = if ($outputs.PSObject.Properties.Name -contains "frontDoorHostName") { $outputs.frontDoorHostName.value } else { "" }
    if (-not $redis) {
        throw "Azure Managed Redis was not deployed. Apply infra/k8s/optional/redis-in-cluster.yaml and set the Redis settings by hand; this script covers the managed path only."
    }

    # One tag is applied to every image. If one image lacks it, that service's new pod waits
    # for ever while the old one keeps answering, which is easy to miss. So look first.
    $images = [regex]::Matches((Get-Manifests $sqlFqdn $sqlLogin $redis $frontDoor), 'image:\s*\S+?azurecr\.io/(\S+)') |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    $missing = @()
    foreach ($image in $images) {
        if (-not (Test-Native { az acr repository show --name $RegistryName --image $image --output none })) { $missing += $image }
    }
    if ($missing) {
        throw "Registry $RegistryName has no image for: $($missing -join ', '). Build every image under one tag first (publish-images.ps1 -ImageTag $ImageTag). Nothing was changed."
    }

    # -------------------------------------------------------------------------
    Write-Host "2. Application secrets in Key Vault $vault (existing ones are kept)" -ForegroundColor Cyan
    # The JWT key has the shape the services already use: 64 hexadecimal characters.
    # The database passwords start with a letter and hold letters and digits only, which
    # Oracle, PostgreSQL and the .env file on the VM all take without quoting.
    $generated = [ordered]@{
        "jwt-secret"               = { New-Hex 32 }
        "internal-service-secret"  = { New-Hex 24 }
        "interbank-shared-secret"  = { New-Hex 24 }
        "interbank-bankb-secret"   = { New-Hex 24 }
        "oracle-password"          = { "Cb" + (New-Hex 12).ToLower() }
        "app-user-password"        = { "Cb" + (New-Hex 12).ToLower() }
        "audit-db-password"        = { "Cb" + (New-Hex 12).ToLower() }
        # For admin and the tellers. Letters and digits only, so it can be typed and passed to Newman unquoted.
        "staff-password"           = { "St" + (New-Hex 8).ToLower() }
    }
    foreach ($name in $generated.Keys) {
        if (Test-Native { az keyvault secret show --vault-name $vault --name $name --query id --output tsv }) {
            Write-Host "   $name exists"
        } else {
            Invoke-Native "az keyvault secret set $name" {
                az keyvault secret set --vault-name $vault --name $name --value (& $generated[$name]) --output none
            } | Out-Null
            Write-Host "   $name created"
        }
    }

    # -------------------------------------------------------------------------
    Write-Host "3. Kubernetes Secret coobs-app-secrets" -ForegroundColor Cyan
    Invoke-Native "az aks get-credentials" {
        az aks get-credentials --resource-group $ResourceGroup --name $cluster --overwrite-existing --output none
    } | Out-Null
    Invoke-Native "kubectl apply namespace" { kubectl apply -f (Join-Path $repo "infra\k8s\namespace.yaml") } | Out-Null

    # A run that only brings a new version must not take HTTPS away. When the cluster has the
    # HTTPS entry and this run names no label, the label and e-mail address in use are kept.
    if (-not $DnsLabel -and -not $PlainHttp) {
        $liveLabel = kubectl get service coobs-public --namespace $namespace --ignore-not-found --output "jsonpath={.metadata.annotations.service\.beta\.kubernetes\.io/azure-dns-label-name}"
        $liveProxy = (kubectl get configmap tls-proxy-config --namespace $namespace --ignore-not-found --output "jsonpath={.data.Caddyfile}") -join "`n"
        if ($liveLabel -and $liveProxy -match '(?m)^\s*email\s+(\S+)') {
            $DnsLabel = $liveLabel
            $AcmeEmail = $Matches[1]
            Write-Host "   HTTPS is on at $DnsLabel.$Location.cloudapp.azure.com and stays on (-PlainHttp turns it off)"
        }
    }

    # Kubernetes key -> Key Vault secret
    $secretMap = [ordered]@{
        JWT_SECRET              = "jwt-secret"
        INTERNAL_SERVICE_SECRET = "internal-service-secret"
        CLOUD_DB_PASSWORD       = "cloud-db-password"
        REDIS_PASSWORD          = "redis-key"
        INTERBANK_SHARED_SECRET = "interbank-shared-secret"
        INTERBANK_BANKB_SECRET  = "interbank-bankb-secret"
        # Read only by the schema job, which stores its hash for the staff sign-ins.
        STAFF_PASSWORD          = "staff-password"
    }
    $fromFile = @()
    foreach ($key in $secretMap.Keys) {
        $value = Invoke-Native "az keyvault secret show $($secretMap[$key])" {
            az keyvault secret show --vault-name $vault --name $secretMap[$key] --query value --output tsv
        }
        $file = Join-Path $work $key
        [System.IO.File]::WriteAllText($file, [string]$value, $noBom)
        $fromFile += "--from-file=$key=$file"
    }
    # Replaced in one step. Deleting it first would leave no Secret at all if the create then
    # failed, and the next pod to restart could not start.
    $rendered = Invoke-Native "kubectl create secret" {
        kubectl create secret generic coobs-app-secrets --namespace $namespace @fromFile --dry-run=client --output yaml
    }
    $secretFile = Join-Path $work "coobs-app-secrets.yaml"
    [System.IO.File]::WriteAllText($secretFile, ($rendered -join "`n"), $noBom)
    Invoke-Native "kubectl apply secret" { kubectl apply -f $secretFile } | Out-Null

    # -------------------------------------------------------------------------
    if ($SkipSchemaLoad) {
        Write-Host "4. Schema load skipped" -ForegroundColor Yellow
    } else {
        if ($ResetData) {
            Write-Host "4. RESET: stopping the services, then emptying Redis and the four databases on $sqlFqdn" -ForegroundColor Yellow
            # A service that writes while the seed is being loaded breaks the load. Step 5 starts
            # them again, because the manifests say one replica each.
            $running = kubectl get deployments --namespace $namespace --output "jsonpath={.items[*].metadata.name}"
            Invoke-Native "kubectl scale" { kubectl scale deployment --all --replicas=0 --namespace $namespace } | Out-Null
            foreach ($name in ($running -split " ")) {
                if ($name) { kubectl wait --for=delete pod --selector "app.kubernetes.io/name=$name" --namespace $namespace --timeout=120s 2>$null | Out-Null }
            }
        } else {
            Write-Host "4. Loading the four databases on $sqlFqdn (the first run pulls a large image)" -ForegroundColor Cyan
        }
        $sql = Join-Path $repo "database\azure-sql"
        kubectl delete job coobs-load-schemas --namespace $namespace --ignore-not-found | Out-Null
        kubectl delete configmap coobs-sql-scripts coobs-sql-target --namespace $namespace --ignore-not-found | Out-Null
        Invoke-Native "kubectl create configmap coobs-sql-scripts" {
            kubectl create configmap coobs-sql-scripts --namespace $namespace `
                "--from-file=$sql\02_identity_db.sql" "--from-file=$sql\03_customer_db.sql" `
                "--from-file=$sql\04_transaction_db.sql" "--from-file=$sql\05_notification_db.sql"
        } | Out-Null
        Invoke-Native "kubectl create configmap coobs-sql-target" {
            kubectl create configmap coobs-sql-target --namespace $namespace `
                "--from-literal=SQL_SERVER_FQDN=$sqlFqdn" "--from-literal=CLOUD_DB_USER=$sqlLogin" `
                "--from-literal=RESET_DATA=$("$ResetData".ToLower())"
        } | Out-Null
        Invoke-Native "kubectl apply job" { kubectl apply -f (Join-Path $repo "infra\k8s\jobs\load-schemas.yaml") } | Out-Null
        # Polled, so that a failed job ends the wait at once instead of running into the timeout.
        $deadline = (Get-Date).AddMinutes(15)
        do {
            Start-Sleep -Seconds 5
            $state = kubectl get job coobs-load-schemas --namespace $namespace --output "jsonpath={.status.succeeded}/{.status.failed}"
        } while ($state -eq "/" -and (Get-Date) -lt $deadline)
        $loaded = $state.StartsWith("1")
        if ($ResetData) { kubectl logs --namespace $namespace job/coobs-load-schemas --container flush-redis --tail=5 }
        kubectl logs --namespace $namespace job/coobs-load-schemas --tail=40
        if (-not $loaded) {
            if ($ResetData) { Write-Host "The services are stopped. Run this script again with -ResetData once the cause is fixed; it starts from an empty state each time." -ForegroundColor Red }
            throw "The schema load did not complete. The log above says which database stopped it."
        }
    }

    # -------------------------------------------------------------------------
    Write-Host "5. Applying infra/k8s with image tag $ImageTag" -ForegroundColor Cyan
    $manifests = Join-Path $work "apply.yaml"
    [System.IO.File]::WriteAllText($manifests, (Get-Manifests $sqlFqdn $sqlLogin $redis $frontDoor), $noBom)
    $applied = Invoke-Native "kubectl apply" { kubectl apply -f $manifests }

    # A pod reads the ConfigMap once, when it starts, and a changed ConfigMap restarts nothing
    # by itself. On 10 October 2026 the gateway kept an old list of allowed origins that way
    # and refused every sign-in from the console. The TLS proxy does not read it and is left alone.
    if ($applied -match "configmap/coobs-config configured") {
        Write-Host "   The settings changed, so the services are restarted to read them"
        foreach ($deployment in (kubectl get deployments --namespace $namespace --output name)) {
            if ($deployment -notmatch "tls-proxy") {
                Invoke-Native "kubectl rollout restart $deployment" { kubectl rollout restart $deployment --namespace $namespace } | Out-Null
            }
        }
    }

    Write-Host "   Waiting for the nine services and the console (they start together on two vCPUs, so this is slow)"
    # The rollout, not "available": a Deployment whose new pod cannot start stays available on
    # its old pod, and a wait for that reports success.
    $rolledOut = $true
    foreach ($deployment in (kubectl get deployments --namespace $namespace --output name)) {
        kubectl rollout status $deployment --namespace $namespace --timeout=900s | Out-Null
        if ($LASTEXITCODE -ne 0) { $rolledOut = $false; break }
    }
    kubectl get pods --namespace $namespace
    if (-not $rolledOut) { throw "Not every deployment became available. The pod list above shows which one; read its log with kubectl logs." }
    # Azure can take a minute or two to give the load balancer its address after the pods are up.
    $deadline = (Get-Date).AddMinutes(3)
    do {
        $address = kubectl get service coobs-public --namespace $namespace --output "jsonpath={.status.loadBalancer.ingress[0].ip}"
        if (-not $address) { Start-Sleep -Seconds 5 }
    } while (-not $address -and (Get-Date) -lt $deadline)
    if (-not $address) {
        throw "There is no public address yet. Look again with: kubectl get service coobs-public --namespace $namespace"
    }

    # -------------------------------------------------------------------------
    # Monitoring is optional and apart from the system. Without the secret datadog-api-key in
    # Key Vault this does nothing, and a failure here does not undo the deployment above.
    # The agent is one pod that reserves 50m of CPU (infra/k8s-monitoring).
    if (Test-Native { az keyvault secret show --vault-name $vault --name datadog-api-key --query id --output tsv }) {
        Write-Host "6. Monitoring: the Datadog agent" -ForegroundColor Cyan
        $site = "datadoghq.com"
        if (Test-Native { az keyvault secret show --vault-name $vault --name datadog-site --query id --output tsv }) {
            $site = ((Invoke-Native "az keyvault secret show datadog-site" {
                az keyvault secret show --vault-name $vault --name datadog-site --query value --output tsv
            }) -join "").Trim()
        }
        if ($site -notmatch '^[a-z0-9.-]+$') { throw "The Key Vault secret datadog-site is not a host name" }
        $agentFile = Join-Path $work "datadog-agent.yaml"
        $agent = [System.IO.File]::ReadAllText((Join-Path $repo "infra\k8s-monitoring\datadog-agent.yaml"))
        [System.IO.File]::WriteAllText($agentFile, $agent.Replace("REPLACE-datadog-site", $site), $noBom)
        Invoke-Native "kubectl apply datadog agent" { kubectl apply -f $agentFile } | Out-Null

        $keyFile = Join-Path $work "api-key"
        $key = Invoke-Native "az keyvault secret show datadog-api-key" {
            az keyvault secret show --vault-name $vault --name datadog-api-key --query value --output tsv
        }
        # Trimmed: a key that was put into the vault from a file often ends with a line break.
        [System.IO.File]::WriteAllText($keyFile, ($key -join "").Trim(), $noBom)
        $rendered = Invoke-Native "kubectl create secret datadog-secret" {
            kubectl create secret generic datadog-secret --namespace datadog "--from-file=api-key=$keyFile" --dry-run=client --output yaml
        }
        $ddSecretFile = Join-Path $work "datadog-secret.yaml"
        [System.IO.File]::WriteAllText($ddSecretFile, ($rendered -join "`n"), $noBom)
        $secretResult = Invoke-Native "kubectl apply datadog-secret" { kubectl apply -f $ddSecretFile }
        # A pod reads the key once, when it starts.
        if ($secretResult -match "configured") { kubectl rollout restart daemonset/datadog --namespace datadog | Out-Null }

        kubectl rollout status daemonset/datadog --namespace datadog --timeout=300s | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "   The agent is running and sends to $site. The VM's agent starts with the next deploy-onprem.ps1."
        } else {
            Write-Host "   The agent did not become ready. The system itself is deployed; look at: kubectl describe pods --namespace datadog" -ForegroundColor Yellow
        }

        # One at a time, each waited for: the node has room for one extra pod during a restart.
        foreach ($service in $TraceServices) {
            if ($service -in "frontend", "tls-proxy" -or -not (Test-Native { kubectl get deployment $service --namespace $namespace --output name })) {
                throw "-TraceServices: '$service' is not one of the Java services in namespace $namespace"
            }
            $patchFile = Join-Path $work "tracer-$service.yaml"
            $patch = [System.IO.File]::ReadAllText((Join-Path $repo "infra\k8s-monitoring\java-tracer-patch.yaml"))
            [System.IO.File]::WriteAllText($patchFile, $patch.Replace("REPLACE-service", $service), $noBom)
            Invoke-Native "kubectl patch $service" {
                kubectl patch deployment $service --namespace $namespace --type strategic --patch-file $patchFile
            } | Out-Null
            kubectl rollout status deployment/$service --namespace $namespace --timeout=600s | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw "$service did not come back with the tracer. Take it off with: kubectl rollout undo deployment/$service --namespace $namespace"
            }
            Write-Host "   $service is traced"
        }
    } else {
        Write-Host "6. Monitoring skipped: Key Vault $vault has no secret datadog-api-key"
        if ($TraceServices) { Write-Host "   -TraceServices was ignored for the same reason" -ForegroundColor Yellow }
    }

    $base = "http://$address"
    if ($DnsLabel) {
        $base = "https://$DnsLabel.$Location.cloudapp.azure.com"
    } elseif ($frontDoor) {
        # From now on the load balancer takes calls from Front Door only, so everything is HTTPS.
        # To open it again: kubectl annotate service coobs-public -n coobs service.beta.kubernetes.io/azure-allowed-service-tags-
        Invoke-Native "kubectl annotate service" {
            kubectl annotate service coobs-public --namespace $namespace "service.beta.kubernetes.io/azure-allowed-service-tags=AzureFrontDoor.Backend" --overwrite
        } | Out-Null
        $base = "https://$frontDoor"
    }

    Write-Host ""
    Write-Host "Staff console: $base" -ForegroundColor Green
    Write-Host "API (the app's gateway address, and baseUrl for Postman): $base" -ForegroundColor Green
    if ($DnsLabel) {
        Write-Host "The certificate is fetched in the first minute or two. If the page does not open by then, the reason is in:"
        Write-Host "  kubectl logs deployment/tls-proxy --namespace $namespace --tail 40"
    } elseif (-not $frontDoor) {
        Write-Host "This is plain HTTP. For HTTPS, deploy the Bicep again with COOBS_FRONT_DOOR=true and COOBS_PUBLIC_ADDRESS=$address, then run this script again."
    }
    Write-Host "Staff sign-ins (admin, tellers) use the generated password, not the seeded one. Read it with:"
    Write-Host "  az keyvault secret show --vault-name $vault --name staff-password --query value --output tsv"
    Write-Host "Members keep the seeded demo password."
    Write-Host "Next, the on-premises zone:"
    Write-Host "  .\infra\scripts\deploy-onprem.ps1 -KeyVaultName $vault -ImageTag $ImageTag -RedisHost $redis"
    Write-Host "Then the E2E suite against this address (the local environment file, with the address and the staff password replaced):"
    Write-Host "  npx --yes newman run postman/e2e/CooBS_End_To_End.postman_collection.json -e postman/CooBS_Local.postman_environment.json --env-var baseUrl=$base --env-var staffPassword=<staff password> --env-var tellerPassword=<staff password> --delay-request 150 --reporters cli"
} finally {
    Get-ChildItem $work -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object { [System.IO.File]::Delete($_.FullName) }
}
