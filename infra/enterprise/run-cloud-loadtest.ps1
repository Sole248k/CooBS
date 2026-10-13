<#
.SYNOPSIS
    Automates cloud-scale load testing against the CooBS enterprise deployment using Azure Load Testing (ALT).

.DESCRIPTION
    1. Reads the Front Door host or ingress public IP and Key Vault secrets from Azure deployment outputs.
    2. Verifies the target is live and answering healthy over /actuator/health.
    3. Provisions or reuses an Azure Load Testing resource (Microsoft.LoadTestService) in your resource group.
    4. Uploads the JMeter test plan (coobs-loadtest.jmx) and configuration (loadtest-config.yaml).
    5. Triggers a cloud-distributed test run from Azure test engines.
    6. Streams metrics, evaluates failure criteria (SLA thresholds), and displays the performance summary.

.EXAMPLE
    .\infra\enterprise\run-cloud-loadtest.ps1

.EXAMPLE
    .\infra\enterprise\run-cloud-loadtest.ps1 -DurationSec 300 -TotalVUs 100
#>

[CmdletBinding()]
param(
    [string]$ResourceGroup = "CooBS-ent-rsg",
    [string]$DeploymentName = "coobs-ent",
    [string]$LoadTestName = "coobs-loadtest",
    [string]$Location = "indonesiacentral",
    [int]$DurationSec = 180,
    [int]$TotalVUs = 75,
    [switch]$SkipProvision
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$loadTestDir = Join-Path $repoRoot "infra\load-testing"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "     CooBS Enterprise Cloud Load Testing Suite (FIN-807)         " -ForegroundColor Cyan
Write-Host "     Targeting Azure Ingress & Microservices with Azure Load Test " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# -----------------------------------------------------------------------------
# 1. DISCOVER CLOUD TARGET ADDRESS & KEY VAULT SECRETS
# -----------------------------------------------------------------------------
Write-Host "`n[1/5] Discovering Azure deployment endpoints..." -ForegroundColor Cyan

$json = az deployment group show --resource-group $ResourceGroup --name $DeploymentName --query properties.outputs --output json
if ($LASTEXITCODE -ne 0) { 
    throw "Could not read outputs of deployment '$DeploymentName' in '$ResourceGroup'. Please ensure you are logged in (az login)." 
}

$outputs = ($json -join "`n") | ConvertFrom-Json
$vault = $outputs.keyVaultName.value
$frontDoor = if ($outputs.PSObject.Properties.Name -contains "frontDoorHostName") { $outputs.frontDoorHostName.value } else { "" }

if ($frontDoor) {
    $targetHost = $frontDoor
    $targetProtocol = "https"
    $targetPort = 443
} else {
    $cluster = $outputs.channelsClusterName.value
    $nodeGroup = ((az aks show --resource-group $ResourceGroup --name $cluster --query nodeResourceGroup --output tsv) -join "").Trim()
    $address = ((az network public-ip list --resource-group $nodeGroup --query "[?starts_with(name, 'kubernetes-')].ipAddress | [0]" --output tsv) -join "").Trim()
    if (-not $address) { 
        throw "Channels AKS cluster has no public IP allocated yet. Has deploy-enterprise.ps1 completed?" 
    }
    $targetHost = $address
    $targetProtocol = "http"
    $targetPort = 80
}

$baseUrl = "$targetProtocol://${targetHost}:$targetPort"
Write-Host "Target Base URL: $baseUrl" -ForegroundColor Green

$password = ((az keyvault secret show --vault-name $vault --name staff-password --query value --output tsv) -join "").Trim()
if (-not $password) { 
    $password = "StaffPass123!"
}

# -----------------------------------------------------------------------------
# 2. VERIFY TARGET HEALTH BEFORE GENERATING LOAD
# -----------------------------------------------------------------------------
Write-Host "`n[2/5] Probing Gateway health status..." -ForegroundColor Cyan
try {
    $health = Invoke-WebRequest -Uri "$baseUrl/actuator/health" -UseBasicParsing -TimeoutSec 15
    if ($health.Content -notmatch '"status"\s*:\s*"UP"') {
        throw "Gateway replied with non-UP status: $($health.Content)"
    }
    Write-Host "Target Gateway is healthy and responding [200 UP]." -ForegroundColor Green
} catch {
    throw "Target at $baseUrl/actuator/health is unreachable or failing: $_"
}

# -----------------------------------------------------------------------------
# 3. ENSURE AZURE LOAD TESTING EXTENSION & RESOURCE EXIST
# -----------------------------------------------------------------------------
Write-Host "`n[3/5] Checking Azure Load Testing CLI extension & resource..." -ForegroundColor Cyan

# Install az load extension if not present
$hasExtension = az extension list --query "[?name=='load'].name" --output tsv
if (-not $hasExtension) {
    Write-Host "Installing 'load' extension for Azure CLI..." -ForegroundColor Yellow
    az extension add --name load --yes | Out-Null
}

$altExists = az load list --resource-group $ResourceGroup --query "[?name=='$LoadTestName'].name | [0]" --output tsv
if (-not $altExists -and -not $SkipProvision) {
    Write-Host "Creating Azure Load Testing resource '$LoadTestName' in $Location..." -ForegroundColor Yellow
    az load create --name $LoadTestName --resource-group $ResourceGroup --location $Location --output none
    Write-Host "Created Azure Load Testing resource: $LoadTestName" -ForegroundColor Green
} else {
    Write-Host "Reusing existing Azure Load Testing resource: $LoadTestName" -ForegroundColor Green
}

# -----------------------------------------------------------------------------
# 4. REGISTER / UPDATE TEST PLAN IN AZURE LOAD TESTING
# -----------------------------------------------------------------------------
Write-Host "`n[4/5] Syncing JMeter Test Plan and SLA threshold configuration..." -ForegroundColor Cyan

$testId = "coobs-core-loadtest"
$jmxPath = Join-Path $loadTestDir "coobs-loadtest.jmx"
$configPath = Join-Path $loadTestDir "loadtest-config.yaml"

$healthVUs = [Math]::Max(10, [int]($TotalVUs * 0.25))
$shopVUs = [Math]::Max(20, [int]($TotalVUs * 0.55))
$authVUs = [Math]::Max(5, [int]($TotalVUs * 0.20))

# Create or update the test definition in Azure Load Testing
az load test create `
    --load-test-resource $LoadTestName `
    --resource-group $ResourceGroup `
    --test-id $testId `
    --display-name "CooBS Enterprise Banking Load Test" `
    --description "Automated concurrency test against Front Door & Microservices" `
    --test-plan $jmxPath `
    --load-test-config-file $configPath `
    --env "TARGET_HOST=$targetHost" "TARGET_PORT=$targetPort" "TARGET_PROTOCOL=$targetProtocol" "STAFF_PASSWORD=$password" "DURATION_SEC=$DurationSec" "HEALTH_VUS=$healthVUs" "SHOP_VUS=$shopVUs" "AUTH_VUS=$authVUs" `
    --output none

Write-Host "Test plan '$testId' uploaded and configured successfully." -ForegroundColor Green

# -----------------------------------------------------------------------------
# 5. EXECUTE THE CLOUD LOAD TEST RUN
# -----------------------------------------------------------------------------
$runId = "run-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Write-Host "`n[5/5] Launching cloud test run: $runId ($TotalVUs VUs for ${DurationSec}s)..." -ForegroundColor Cyan

az load test-run create `
    --load-test-resource $LoadTestName `
    --resource-group $ResourceGroup `
    --test-id $testId `
    --test-run-id $runId `
    --display-name "CooBS Automated Run $runId" `
    --output none

Write-Host "Test run launched. Polling status until completion..." -ForegroundColor Yellow

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
do {
    Start-Sleep -Seconds 15
    $runStatus = az load test-run show `
        --load-test-resource $LoadTestName `
        --resource-group $ResourceGroup `
        --test-run-id $runId `
        --query status `
        --output tsv
    
    $elapsed = [int]$stopwatch.Elapsed.TotalSeconds
    Write-Host "  [$elapsed s elapsed] Status: $runStatus" -ForegroundColor Gray
} while ($runStatus -in @("ACCEPTED", "PROVISIONING", "CONFIGURING", "EXECUTING"))

$stopwatch.Stop()

# Fetch final metrics
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "                     TEST RUN RESULTS                            " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$summary = az load test-run show `
    --load-test-resource $LoadTestName `
    --resource-group $ResourceGroup `
    --test-run-id $runId `
    --output json | ConvertFrom-Json

Write-Host "Final Status: $($summary.status)" -ForegroundColor $(if ($summary.status -eq "DONE") { "Green" } else { "Red" })
Write-Host "Pass/Fail Result: $($summary.testResult)" -ForegroundColor $(if ($summary.testResult -eq "PASSED") { "Green" } else { "Red" })

if ($summary.PSObject.Properties.Name -contains "testRunStatistics") {
    Write-Host "`nAggregated Metrics:" -ForegroundColor Cyan
    $summary.testRunStatistics.PSObject.Properties | ForEach-Object {
        $metric = $_.Value
        Write-Host "  Transaction: $($_.Name)" -ForegroundColor Yellow
        Write-Host "    - Mean Latency : $($metric.meanResTime) ms"
        Write-Host "    - P95 Latency  : $($metric.pct95ResTime) ms (SLA Threshold: <1500 ms)"
        Write-Host "    - P99 Latency  : $($metric.pct99ResTime) ms (SLA Threshold: <500 ms)"
        Write-Host "    - Error Rate   : $($metric.errorPct) %"
        Write-Host "    - Throughput   : $($metric.throughput) req/s"
    }
}

Write-Host "`nTo view full graphical telemetry, open Azure Portal -> Azure Load Testing -> $LoadTestName -> $runId" -ForegroundColor Cyan
