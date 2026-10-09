<#
.SYNOPSIS
    Deploys the cloud zone to the AKS cluster the Bicep created.
.DESCRIPTION
    Run after `az deployment group create` and after the images are in the registry. In order:

      1. Reads the Bicep outputs (cluster, Key Vault, Azure SQL, Redis).
      2. Generates the application secrets into Key Vault. A secret that already exists is kept.
      3. Creates the Kubernetes Secret coobs-app-secrets from Key Vault.
      4. Loads the four databases from database/azure-sql/ with a Job in the cluster.
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
    .\infra\scripts\deploy-cloud.ps1 -ImageTag t -RenderOnly rendered.yaml -SqlServerFqdn s.database.windows.net -SqlAdminLogin coobsadmin -RedisHost r.redis.azure.net
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$ImageTag,
    [string]$ResourceGroup = "CooBS-rsg",
    [string]$DeploymentName = "coobs-demo",
    [switch]$SkipSchemaLoad,
    # Writes the manifests that would be applied to this file and stops, without calling Azure or the cluster.
    # Needs the three values below, which otherwise come from the Bicep outputs.
    [string]$RenderOnly,
    [string]$SqlServerFqdn,
    [string]$SqlAdminLogin,
    [string]$RedisHost
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
function Get-Manifests([string]$sqlFqdn, [string]$sqlLogin, [string]$redis) {
    $rendered = Join-Path $work "rendered.yaml"
    Invoke-Native "kubectl kustomize" { kubectl kustomize (Join-Path $repo "infra\k8s") --output $rendered } | Out-Null
    $text = [System.IO.File]::ReadAllText($rendered)
    $text = $text.Replace("REPLACE-server-name.database.windows.net", $sqlFqdn)
    $text = $text.Replace("REPLACE-sql-admin-login", $sqlLogin)
    $text = $text.Replace("REPLACE-cache-name.indonesiacentral.redis.azure.net", $redis)
    $text = $text.Replace(":CHANGE_ME", ":$ImageTag")
    if ($text -match "REPLACE-|CHANGE_ME") { throw "A placeholder is still in the rendered manifests: $($Matches[0])" }
    return $text
}

try {
    if ($RenderOnly) {
        foreach ($name in "SqlServerFqdn", "SqlAdminLogin", "RedisHost") {
            if (-not (Get-Variable $name -ValueOnly)) { throw "-RenderOnly needs -$name" }
        }
        [System.IO.File]::WriteAllText($RenderOnly, (Get-Manifests $SqlServerFqdn $SqlAdminLogin $RedisHost), $noBom)
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
    if (-not $redis) {
        throw "Azure Managed Redis was not deployed. Apply infra/k8s/optional/redis-in-cluster.yaml and set the Redis settings by hand; this script covers the managed path only."
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

    # Kubernetes key -> Key Vault secret
    $secretMap = [ordered]@{
        JWT_SECRET              = "jwt-secret"
        INTERNAL_SERVICE_SECRET = "internal-service-secret"
        CLOUD_DB_PASSWORD       = "cloud-db-password"
        REDIS_PASSWORD          = "redis-key"
        INTERBANK_SHARED_SECRET = "interbank-shared-secret"
        INTERBANK_BANKB_SECRET  = "interbank-bankb-secret"
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
    kubectl delete secret coobs-app-secrets --namespace $namespace --ignore-not-found | Out-Null
    Invoke-Native "kubectl create secret" { kubectl create secret generic coobs-app-secrets --namespace $namespace @fromFile } | Out-Null

    # -------------------------------------------------------------------------
    if ($SkipSchemaLoad) {
        Write-Host "4. Schema load skipped" -ForegroundColor Yellow
    } else {
        Write-Host "4. Loading the four databases on $sqlFqdn (the first run pulls a large image)" -ForegroundColor Cyan
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
                "--from-literal=SQL_SERVER_FQDN=$sqlFqdn" "--from-literal=CLOUD_DB_USER=$sqlLogin"
        } | Out-Null
        Invoke-Native "kubectl apply job" { kubectl apply -f (Join-Path $repo "infra\k8s\jobs\load-schemas.yaml") } | Out-Null
        # Polled, so that a failed job ends the wait at once instead of running into the timeout.
        $deadline = (Get-Date).AddMinutes(15)
        do {
            Start-Sleep -Seconds 5
            $state = kubectl get job coobs-load-schemas --namespace $namespace --output "jsonpath={.status.succeeded}/{.status.failed}"
        } while ($state -eq "/" -and (Get-Date) -lt $deadline)
        $loaded = $state.StartsWith("1")
        kubectl logs --namespace $namespace job/coobs-load-schemas --tail=40
        if (-not $loaded) { throw "The schema load did not complete. The log above says which database stopped it." }
    }

    # -------------------------------------------------------------------------
    Write-Host "5. Applying infra/k8s with image tag $ImageTag" -ForegroundColor Cyan
    $manifests = Join-Path $work "apply.yaml"
    [System.IO.File]::WriteAllText($manifests, (Get-Manifests $sqlFqdn $sqlLogin $redis), $noBom)
    Invoke-Native "kubectl apply" { kubectl apply -f $manifests } | Out-Null

    Write-Host "   Waiting for the eight services (they start together on two vCPUs, so this is slow)"
    kubectl wait --namespace $namespace --for=condition=available --timeout=900s deployment --all | Out-Null
    kubectl get pods --namespace $namespace
    $address = kubectl get service api-gateway-public --namespace $namespace --output "jsonpath={.status.loadBalancer.ingress[0].ip}"

    Write-Host ""
    Write-Host "Gateway: http://$address" -ForegroundColor Green
    Write-Host "Next, the on-premises zone:"
    Write-Host "  .\infra\scripts\deploy-onprem.ps1 -KeyVaultName $vault -ImageTag $ImageTag -RedisHost $redis"
} finally {
    Get-ChildItem $work -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object { [System.IO.File]::Delete($_.FullName) }
}
