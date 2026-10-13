<#
.SYNOPSIS
    Deploys the two-cluster layout: the services cluster first, then the channels cluster.

.DESCRIPTION
    The enterprise-style twin of infra/scripts/deploy-cloud.ps1, for the resource group that
    infra/enterprise/main.bicep created. It never touches the demo deployment in CooBS-rsg.

      1. Reads the Bicep outputs and checks the registry holds every image under the tag.
      2. Generates the application secrets into Key Vault (existing ones are kept).
      3. Services cluster: namespace, Secret, schema load, infra/enterprise/k8s/services.
      4. Channels cluster: namespace, Secret, infra/enterprise/k8s/channels.
      5. Prints the public address and the commands for Front Door, the on-premises zone and E2E.

    It changes Azure and both clusters, so run it yourself. It needs the Azure CLI, signed in,
    and kubectl. Left out on purpose, to keep the first run small: the tracer, the
    in-cluster TLS proxy and the data reset. HTTPS comes from Front Door (README.md).

.EXAMPLE
    .\infra\enterprise\deploy-enterprise.ps1 -ImageTag ent-1 -RegistryName coobsentacr

.EXAMPLE
    .\infra\enterprise\deploy-enterprise.ps1 -ImageTag t -RegistryName r -RenderOnly out -SqlServerFqdn s.database.windows.net -SqlAdminLogin coobsadmin -RedisHost r.redis.azure.net
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$ImageTag,
    # The registry in the enterprise resource group, by its Azure resource name.
    [Parameter(Mandatory)] [string]$RegistryName,
    [string]$ResourceGroup = "CooBS-ent-rsg",
    [string]$DeploymentName = "coobs-ent",
    # The databases are loaded once. Use this on every later run.
    [switch]$SkipSchemaLoad,
    # Writes services.yaml and channels.yaml into this folder and stops, without calling Azure
    # or a cluster. Needs the three values below, which otherwise come from the Bicep outputs.
    [string]$RenderOnly,
    [string]$SqlServerFqdn,
    [string]$SqlAdminLogin,
    [string]$RedisHost,
    [string]$FrontDoorHost
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$namespace = "coobs"
$noBom = New-Object System.Text.UTF8Encoding $false
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("coobs-ent-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
New-Item -ItemType Directory -Path $work | Out-Null
# The login server written in infra/k8s/kustomization.yaml, replaced by this deployment's registry.
$demoRegistry = "coobs-asfagkfjadgsgfcf.azurecr.io"

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

# Renders one overlay (services or channels) and puts the deployment's values in place of the placeholders.
function Get-Manifests([string]$overlay, [string]$loginServer, [string]$sqlFqdn, [string]$sqlLogin, [string]$redis, [string]$frontDoor) {
    $rendered = Join-Path $work "$overlay-rendered.yaml"
    Invoke-Native "kubectl kustomize $overlay" { kubectl kustomize (Join-Path $PSScriptRoot "k8s\$overlay") --output $rendered } | Out-Null
    $text = [System.IO.File]::ReadAllText($rendered)
    $text = $text.Replace("REPLACE-server-name.database.windows.net", $sqlFqdn)
    $text = $text.Replace("REPLACE-sql-admin-login", $sqlLogin)
    $text = $text.Replace("REPLACE-cache-name.indonesiacentral.redis.azure.net", $redis)
    $text = $text.Replace($demoRegistry, $loginServer)
    $text = $text.Replace(":CHANGE_ME", ":$ImageTag")
    if ($frontDoor) {
        # Behind Front Door the browser's origin is its host name, which the gateway must allow.
        $listed = "http://localhost:3000,http://localhost:5173"
        if (-not $text.Contains($listed)) { throw "CORS_ALLOWED_ORIGINS in infra/k8s/config.yaml is no longer the list this script adds the HTTPS origin to" }
        $text = $text.Replace($listed, "$listed,https://$frontDoor")
    }
    if ($text -match "REPLACE-|CHANGE_ME") { throw "A placeholder is still in the rendered manifests: $($Matches[0])" }
    return $text
}

# Namespace and the one Secret, in the cluster the context names.
function Set-ClusterBasics([string]$cluster, [string]$secretFile) {
    Invoke-Native "az aks get-credentials $cluster" {
        az aks get-credentials --resource-group $ResourceGroup --name $cluster --context $cluster --overwrite-existing --output none
    } | Out-Null
    Invoke-Native "kubectl apply namespace" { kubectl --context $cluster apply -f (Join-Path $repo "infra\k8s\namespace.yaml") } | Out-Null
    Invoke-Native "kubectl apply secret" { kubectl --context $cluster apply -f $secretFile } | Out-Null
}

# Applies one overlay and waits for every Deployment in the namespace to finish its rollout.
function Publish-Overlay([string]$cluster, [string]$overlay, [string]$text) {
    $file = Join-Path $work "$overlay-apply.yaml"
    [System.IO.File]::WriteAllText($file, $text, $noBom)
    $applied = Invoke-Native "kubectl apply $overlay" { kubectl --context $cluster apply -f $file }
    # A pod reads the ConfigMap once, when it starts, and a changed ConfigMap restarts nothing by itself.
    if ($applied -match "configmap/coobs-config configured") {
        foreach ($deployment in (kubectl --context $cluster get deployments --namespace $namespace --output name)) {
            Invoke-Native "kubectl rollout restart $deployment" { kubectl --context $cluster rollout restart $deployment --namespace $namespace } | Out-Null
        }
    }
    $rolledOut = $true
    foreach ($deployment in (kubectl --context $cluster get deployments --namespace $namespace --output name)) {
        kubectl --context $cluster rollout status $deployment --namespace $namespace --timeout=900s | Out-Null
        if ($LASTEXITCODE -ne 0) { $rolledOut = $false; break }
    }
    kubectl --context $cluster get pods --namespace $namespace
    if (-not $rolledOut) { throw "Not every deployment in $cluster became available. The pod list above shows which one; read its log with: kubectl --context $cluster logs <pod> --namespace $namespace" }
}

try {
    if ($RenderOnly) {
        foreach ($name in "SqlServerFqdn", "SqlAdminLogin", "RedisHost") {
            if (-not (Get-Variable $name -ValueOnly)) { throw "-RenderOnly needs -$name" }
        }
        New-Item -ItemType Directory -Path $RenderOnly -Force | Out-Null
        foreach ($overlay in "services", "channels") {
            $text = Get-Manifests $overlay "$($RegistryName.ToLower()).azurecr.io" $SqlServerFqdn $SqlAdminLogin $RedisHost $FrontDoorHost
            [System.IO.File]::WriteAllText((Join-Path (Resolve-Path $RenderOnly) "$overlay.yaml"), $text, $noBom)
        }
        Write-Host "Rendered services.yaml and channels.yaml into $RenderOnly. Nothing was sent to Azure or a cluster."
        return
    }

    # -------------------------------------------------------------------------
    Write-Host "1. Reading the outputs of deployment $DeploymentName in $ResourceGroup" -ForegroundColor Cyan
    $json = Invoke-Native "az deployment group show" {
        az deployment group show --resource-group $ResourceGroup --name $DeploymentName --query properties.outputs --output json
    }
    $outputs = ($json -join "`n") | ConvertFrom-Json
    $servicesCluster = $outputs.servicesClusterName.value
    $channelsCluster = $outputs.channelsClusterName.value
    $vault = $outputs.keyVaultName.value
    $sqlFqdn = $outputs.sqlServerFqdn.value
    $sqlLogin = $outputs.sqlAdminLogin.value
    $redis = $outputs.redisHostName.value
    # Empty until the Bicep is deployed again with Front Door switched on (README.md).
    $frontDoor = if ($outputs.PSObject.Properties.Name -contains "frontDoorHostName") { $outputs.frontDoorHostName.value } else { "" }
    if (-not $redis) { throw "Azure Managed Redis was not deployed. This script covers the managed path only." }
    $loginServer = ((Invoke-Native "az acr show" { az acr show --name $RegistryName --resource-group $ResourceGroup --query loginServer --output tsv }) -join "").Trim()

    $servicesText = Get-Manifests "services" $loginServer $sqlFqdn $sqlLogin $redis $frontDoor
    $channelsText = Get-Manifests "channels" $loginServer $sqlFqdn $sqlLogin $redis $frontDoor

    # One tag is applied to every image. If one image lacks it, that service's pod waits for ever. So look first.
    $images = [regex]::Matches($servicesText + $channelsText, 'image:\s*\S+?azurecr\.io/(\S+)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    $missing = @()
    foreach ($image in $images) {
        if (-not (Test-Native { az acr repository show --name $RegistryName --image $image --output none })) { $missing += $image }
    }
    if ($missing) {
        throw "Registry $RegistryName has no image for: $($missing -join ', '). Build every image first: .\infra\scripts\publish-images.ps1 -ImageTag $ImageTag -Registry $loginServer. Nothing was changed."
    }

    # -------------------------------------------------------------------------
    Write-Host "2. Application secrets in Key Vault $vault (existing ones are kept)" -ForegroundColor Cyan
    # Same names and shapes as deploy-cloud.ps1, so deploy-onprem.ps1 and start.sh read them unchanged.
    $generated = [ordered]@{
        "jwt-secret"               = { New-Hex 32 }
        "internal-service-secret"  = { New-Hex 24 }
        "interbank-shared-secret"  = { New-Hex 24 }
        "interbank-bankb-secret"   = { New-Hex 24 }
        "oracle-password"          = { "Cb" + (New-Hex 12).ToLower() }
        "app-user-password"        = { "Cb" + (New-Hex 12).ToLower() }
        "audit-db-password"        = { "Cb" + (New-Hex 12).ToLower() }
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

    # Kubernetes key -> Key Vault secret. Both clusters get the same Secret.
    $secretMap = [ordered]@{
        JWT_SECRET              = "jwt-secret"
        INTERNAL_SERVICE_SECRET = "internal-service-secret"
        CLOUD_DB_PASSWORD       = "cloud-db-password"
        REDIS_PASSWORD          = "redis-key"
        INTERBANK_SHARED_SECRET = "interbank-shared-secret"
        INTERBANK_BANKB_SECRET  = "interbank-bankb-secret"
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
    $rendered = Invoke-Native "kubectl create secret" {
        kubectl create secret generic coobs-app-secrets --namespace $namespace @fromFile --dry-run=client --output yaml
    }
    $secretFile = Join-Path $work "coobs-app-secrets.yaml"
    [System.IO.File]::WriteAllText($secretFile, ($rendered -join "`n"), $noBom)

    # -------------------------------------------------------------------------
    Write-Host "3. Services cluster $servicesCluster" -ForegroundColor Cyan
    Set-ClusterBasics $servicesCluster $secretFile
    if ($SkipSchemaLoad) {
        Write-Host "   Schema load skipped" -ForegroundColor Yellow
    } else {
        Write-Host "   Loading the five databases on $sqlFqdn (the first run pulls a large image)"
        $sql = Join-Path $repo "database\azure-sql"
        kubectl --context $servicesCluster delete job coobs-load-schemas --namespace $namespace --ignore-not-found | Out-Null
        kubectl --context $servicesCluster delete configmap coobs-sql-scripts coobs-sql-target --namespace $namespace --ignore-not-found | Out-Null
        Invoke-Native "kubectl create configmap coobs-sql-scripts" {
            kubectl --context $servicesCluster create configmap coobs-sql-scripts --namespace $namespace `
                "--from-file=$sql\02_identity_db.sql" "--from-file=$sql\03_customer_db.sql" `
                "--from-file=$sql\04_transaction_db.sql" "--from-file=$sql\05_notification_db.sql" `
                "--from-file=$sql\06_order_db.sql"
        } | Out-Null
        Invoke-Native "kubectl create configmap coobs-sql-target" {
            kubectl --context $servicesCluster create configmap coobs-sql-target --namespace $namespace `
                "--from-literal=SQL_SERVER_FQDN=$sqlFqdn" "--from-literal=CLOUD_DB_USER=$sqlLogin" "--from-literal=RESET_DATA=false"
        } | Out-Null
        Invoke-Native "kubectl apply job" { kubectl --context $servicesCluster apply -f (Join-Path $repo "infra\k8s\jobs\load-schemas.yaml") } | Out-Null
        $deadline = (Get-Date).AddMinutes(15)
        do {
            Start-Sleep -Seconds 5
            $state = kubectl --context $servicesCluster get job coobs-load-schemas --namespace $namespace --output "jsonpath={.status.succeeded}/{.status.failed}"
        } while ($state -eq "/" -and (Get-Date) -lt $deadline)
        kubectl --context $servicesCluster logs --namespace $namespace job/coobs-load-schemas --tail=40
        if (-not $state.StartsWith("1")) { throw "The schema load did not complete. The log above says which database stopped it." }
    }
    Write-Host "   Applying infra/enterprise/k8s/services with image tag $ImageTag (seven services and Kafka start together; this is slow)"
    Publish-Overlay $servicesCluster "services" $servicesText

    # -------------------------------------------------------------------------
    Write-Host "4. Channels cluster $channelsCluster" -ForegroundColor Cyan
    Set-ClusterBasics $channelsCluster $secretFile
    Write-Host "   Applying infra/enterprise/k8s/channels with image tag $ImageTag"
    Publish-Overlay $channelsCluster "channels" $channelsText
    # Azure can take a minute or two to give the load balancer its address after the pods are up.
    $deadline = (Get-Date).AddMinutes(3)
    do {
        $address = kubectl --context $channelsCluster get service coobs-public --namespace $namespace --output "jsonpath={.status.loadBalancer.ingress[0].ip}"
        if (-not $address) { Start-Sleep -Seconds 5 }
    } while (-not $address -and (Get-Date) -lt $deadline)
    if (-not $address) { throw "There is no public address yet. Look again with: kubectl --context $channelsCluster get service coobs-public --namespace $namespace" }

    # -------------------------------------------------------------------------
    # Monitoring is optional and apart from the system: without the secret datadog-api-key in
    # this deployment's Key Vault nothing is applied, and a failure here undoes nothing above.
    # One agent pod per cluster (infra/k8s-monitoring), each reserving 50m of CPU.
    if (Test-Native { az keyvault secret show --vault-name $vault --name datadog-api-key --query id --output tsv }) {
        Write-Host "5. Monitoring: the Datadog agent on both clusters" -ForegroundColor Cyan
        $site = "datadoghq.com"
        if (Test-Native { az keyvault secret show --vault-name $vault --name datadog-site --query id --output tsv }) {
            $site = ((Invoke-Native "az keyvault secret show datadog-site" {
                az keyvault secret show --vault-name $vault --name datadog-site --query value --output tsv
            }) -join "").Trim()
        }
        if ($site -notmatch '^[a-z0-9.-]+$') { throw "The Key Vault secret datadog-site is not a host name" }
        $keyFile = Join-Path $work "api-key"
        $key = Invoke-Native "az keyvault secret show datadog-api-key" {
            az keyvault secret show --vault-name $vault --name datadog-api-key --query value --output tsv
        }
        # Trimmed: a key that was put into the vault from a file often ends with a line break.
        [System.IO.File]::WriteAllText($keyFile, ($key -join "").Trim(), $noBom)
        $agent = [System.IO.File]::ReadAllText((Join-Path $repo "infra\k8s-monitoring\datadog-agent.yaml"))
        if (-not $agent.Contains('"aks-coobs-dev"')) { throw "infra/k8s-monitoring/datadog-agent.yaml no longer names the cluster aks-coobs-dev, which this script replaces" }
        foreach ($cluster in $servicesCluster, $channelsCluster) {
            # Each cluster under its own name and the environment "enterprise", so Datadog keeps
            # this deployment apart from the demo one, which reports as "demo".
            $agentFile = Join-Path $work "datadog-agent-$cluster.yaml"
            # -replace for the last one: the file's line endings differ between checkouts.
            $text = $agent.Replace("REPLACE-datadog-site", $site).Replace('"aks-coobs-dev"', "`"$cluster`"") -replace '(name: DD_ENV\r?\n\s+value: )"demo"', '$1"enterprise"'
            if ($text -notmatch 'value: "enterprise"') { throw "DD_ENV was not found in infra/k8s-monitoring/datadog-agent.yaml" }
            [System.IO.File]::WriteAllText($agentFile, $text, $noBom)
            Invoke-Native "kubectl apply datadog agent" { kubectl --context $cluster apply -f $agentFile } | Out-Null
            $rendered = Invoke-Native "kubectl create secret datadog-secret" {
                kubectl create secret generic datadog-secret --namespace datadog "--from-file=api-key=$keyFile" --dry-run=client --output yaml
            }
            $ddSecretFile = Join-Path $work "datadog-secret.yaml"
            [System.IO.File]::WriteAllText($ddSecretFile, ($rendered -join "`n"), $noBom)
            $secretResult = Invoke-Native "kubectl apply datadog-secret" { kubectl --context $cluster apply -f $ddSecretFile }
            # A pod reads the key once, when it starts.
            if ($secretResult -match "configured") { kubectl --context $cluster rollout restart daemonset/datadog --namespace datadog | Out-Null }
            kubectl --context $cluster rollout status daemonset/datadog --namespace datadog --timeout=300s | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Write-Host "   $cluster : the agent is running and sends to $site"
            } else {
                Write-Host "   $cluster : the agent did not become ready. The system itself is deployed; look at: kubectl --context $cluster describe pods --namespace datadog" -ForegroundColor Yellow
            }
        }
    } else {
        Write-Host "5. Monitoring skipped: Key Vault $vault has no secret datadog-api-key (README.md, Monitoring)"
    }

    $base = "http://$address"
    if ($frontDoor) {
        # From now on the load balancer takes calls from Front Door only, so everything is HTTPS.
        Invoke-Native "kubectl annotate service" {
            kubectl --context $channelsCluster annotate service coobs-public --namespace $namespace "service.beta.kubernetes.io/azure-allowed-service-tags=AzureFrontDoor.Backend" --overwrite
        } | Out-Null
        $base = "https://$frontDoor"
    }

    Write-Host ""
    Write-Host "Staff console and API: $base" -ForegroundColor Green
    if (-not $frontDoor) {
        Write-Host "This is plain HTTP. For HTTPS through Front Door, set COOBS_FRONT_DOOR=true and COOBS_PUBLIC_ADDRESS=$address, deploy the Bicep again, then run this script again with -SkipSchemaLoad."
    }
    Write-Host "Staff password: az keyvault secret show --vault-name $vault --name staff-password --query value --output tsv"
    Write-Host "Next, the on-premises zone:"
    Write-Host "  .\infra\scripts\deploy-onprem.ps1 -ResourceGroup $ResourceGroup -KeyVaultName $vault -ImageTag $ImageTag -RedisHost $redis -Registry $loginServer"
    Write-Host "Then the E2E suite:"
    Write-Host "  npx --yes newman run postman/e2e/CooBS_End_To_End.postman_collection.json -e postman/CooBS_Local.postman_environment.json --env-var baseUrl=$base --env-var staffPassword=<staff password> --env-var tellerPassword=<staff password> --delay-request 150 --reporters cli"
} finally {
    Get-ChildItem $work -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object { [System.IO.File]::Delete($_.FullName) }
}
