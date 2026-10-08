<#
.SYNOPSIS
    Automated verification suite for FIN-807: Production Cloud Hardening (Key Vault, TLS & Grafana Dashboards).
.DESCRIPTION
    Validates:
      1. Azure Key Vault Bicep modules, parameters, and secret definitions.
      2. Prometheus 5s scraping configuration & coverage of all 12 microservices.
      3. Prometheus SLA violation alerting rules (Risk Engine 40ms, Gateway 500ms, Saga 1500ms, 429 surges).
      4. Grafana operational & executive SLA dashboard panels.
      5. API Gateway TLS configuration and Kubernetes Ingress TLS manifests.
      6. Live endpoints if containers are running.
#>

[CmdletBinding()]
param(
    [switch]$CheckLiveContainers
)

$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "    CooBS Banking Platform - FIN-807 Observability & TLS Suite   " -ForegroundColor Cyan
Write-Host "    Key Vault Secrets, 5s Prometheus Scraping & Grafana SLAs    " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$WorkspaceRoot = (Resolve-Path "$PSScriptRoot/..").Path
$Passed = 0
$Failed = 0

function Assert-Check {
    param(
        [string]$Name,
        [bool]$Condition,
        [string]$Details = ""
    )
    if ($Condition) {
        Write-Host "  [PASS] $Name" -ForegroundColor Green
        if ($Details) { Write-Host "         $Details" -ForegroundColor Gray }
        $script:Passed++
    } else {
        Write-Host "  [FAIL] $Name" -ForegroundColor Red
        if ($Details) { Write-Host "         $Details" -ForegroundColor Yellow }
        $script:Failed++
    }
}

# ==============================================================================
# SECTION 1: Azure Key Vault Secrets Hardening (Bicep)
# ==============================================================================
Write-Host ""
Write-Host "[1/5] Validating Azure Key Vault Secrets & Bicep IaC..." -ForegroundColor Cyan

$KvBicepPath = Join-Path $WorkspaceRoot "infra/bicep/modules/keyvault.bicep"
$MainBicepPath = Join-Path $WorkspaceRoot "infra/bicep/main.bicep"
$ParamBicepPath = Join-Path $WorkspaceRoot "infra/bicep/main.bicepparam"

Assert-Check "keyvault.bicep exists" (Test-Path $KvBicepPath)
$KvContent = if (Test-Path $KvBicepPath) { Get-Content $KvBicepPath -Raw } else { "" }

$RequiredSecrets = @(
    "jwt-secret",
    "postgres-password",
    "internal-service-secret",
    "oracle-password",
    "eventhubs-connection-string",
    "redis-key"
)

foreach ($sec in $RequiredSecrets) {
    Assert-Check "Key Vault secret definition: '$sec'" ($KvContent -match "name:\s*'$sec'")
}

$MainContent = if (Test-Path $MainBicepPath) { Get-Content $MainBicepPath -Raw } else { "" }
Assert-Check "main.bicep wires internalServiceSecret" ($MainContent -match "internalServiceSecret:\s*internalServiceSecret")
Assert-Check "main.bicep wires oraclePassword" ($MainContent -match "oraclePassword:\s*oraclePassword")
Assert-Check "main.bicep wires eventHubsConnectionString" ($MainContent -match "eventHubsConnectionString:\s*eventhubs\.outputs\.primaryConnectionString")
Assert-Check "main.bicep wires redisKey" ($MainContent -match "redisKey:\s*redis\.outputs\.redisPrimaryKey")

$ParamContent = if (Test-Path $ParamBicepPath) { Get-Content $ParamBicepPath -Raw } else { "" }
Assert-Check "main.bicepparam contains internalServiceSecret" ($ParamContent -match "internalServiceSecret\s*=")
Assert-Check "main.bicepparam contains oraclePassword" ($ParamContent -match "oraclePassword\s*=")

# ==============================================================================
# SECTION 2: Prometheus 5s Scraping Configuration
# ==============================================================================
Write-Host ""
Write-Host "[2/5] Validating Prometheus 5s Scraping Configuration..." -ForegroundColor Cyan

$PromYamlPath = Join-Path $WorkspaceRoot "monitoring/prometheus/prometheus.yml"
Assert-Check "prometheus.yml exists" (Test-Path $PromYamlPath)

$PromContent = if (Test-Path $PromYamlPath) { Get-Content $PromYamlPath -Raw } else { "" }
Assert-Check "Prometheus global scrape_interval is 5s" ($PromContent -match "scrape_interval:\s*5s")
Assert-Check "Prometheus global evaluation_interval is 5s" ($PromContent -match "evaluation_interval:\s*5s")
Assert-Check "Prometheus alerts.yml referenced" ($PromContent -match 'rule_files:\s*\n\s*-\s*"alerts\.yml"')

$RequiredServices = @(
    "api-gateway:8080",
    "auth-service:8081",
    "account-service:8082",
    "ledger-service:8083",
    "notification-service:8084",
    "audit-service:8085",
    "customer-service:8089",
    "transaction-service:8090",
    "orchestration-service:8091",
    "risk-engine:8092",
    "batch-service:8093",
    "mobile-bff:8094"
)

foreach ($svc in $RequiredServices) {
    Assert-Check "Prometheus scrape target: $svc" ($PromContent -match [regex]::Escape($svc))
}

# ==============================================================================
# SECTION 3: Prometheus Alerting Rules & SLA Violations
# ==============================================================================
Write-Host ""
Write-Host "[3/5] Validating Prometheus Alerting Rules & Contractual SLAs..." -ForegroundColor Cyan

$AlertsYamlPath = Join-Path $WorkspaceRoot "monitoring/prometheus/alerts.yml"
Assert-Check "alerts.yml exists" (Test-Path $AlertsYamlPath)

$AlertsContent = if (Test-Path $AlertsYamlPath) { Get-Content $AlertsYamlPath -Raw } else { "" }

$RequiredAlerts = @(
    "RiskEvaluationSlaBreachP95",
    "GatewayEdgeIngressSlaBreachP99",
    "TransferSagaLatencySlaBreachP95",
    "HighRateLimitBurstSurge",
    "SlowTransferLatencyP99",
    "HighHttp5xxRate",
    "ServiceDown",
    "HikariConnectionPoolSaturation",
    "KafkaConsumerLagHigh"
)

foreach ($alert in $RequiredAlerts) {
    Assert-Check "Alert rule defined: $alert" ($AlertsContent -match "alert:\s*$alert")
}

Assert-Check "Risk evaluation SLA expression threshold is 40ms (0.040s)" ($AlertsContent -match "RiskEvaluationSlaBreachP95[\s\S]*?>\s*0\.04")
Assert-Check "Gateway ingress SLA expression threshold is 500ms (0.500s)" ($AlertsContent -match "GatewayEdgeIngressSlaBreachP99[\s\S]*?>\s*0\.5")
Assert-Check "Transfer saga SLA expression threshold is 1500ms (1.500s)" ($AlertsContent -match "TransferSagaLatencySlaBreachP95[\s\S]*?>\s*1\.5")

# ==============================================================================
# SECTION 4: Grafana Executive & Operational Dashboards
# ==============================================================================
Write-Host ""
Write-Host "[4/5] Validating Grafana Operational Command Center Dashboard..." -ForegroundColor Cyan

$DashboardPath = Join-Path $WorkspaceRoot "monitoring/grafana/dashboards/coobs_core_banking.json"
Assert-Check "coobs_core_banking.json exists" (Test-Path $DashboardPath)

if (Test-Path $DashboardPath) {
    try {
        $DashboardJson = Get-Content $DashboardPath -Raw | ConvertFrom-Json
        Assert-Check "Dashboard JSON is valid and well-formed" ($null -ne $DashboardJson)
        
        $PanelTitles = @($DashboardJson.panels | ForEach-Object { $_.title })
        
        Assert-Check "Executive SLA Row present" ($PanelTitles -contains "EXECUTIVE SLA COMPLIANCE & FRAUD SCORING (FIN-807)")
        Assert-Check "Risk Engine SLA Panel present" ($PanelTitles -contains "Risk Engine P95 Latency SLA (<40ms)")
        Assert-Check "Edge Gateway SLA Panel present" ($PanelTitles -contains "Edge Gateway P99 Ingress SLA (<500ms)")
        Assert-Check "Transfer Saga SLA Panel present" ($PanelTitles -contains "Transfer Saga P95 Latency SLA (<1500ms)")
        Assert-Check "Active Alerts Stat Panel present" ($PanelTitles -contains "Active SLA & System Alerts Firing")
        Assert-Check "Risk Engine Latency Percentiles Timeseries present" ($PanelTitles -contains "Risk Engine Evaluation Latency (P50, P90, P95 vs 40ms SLA)")
        Assert-Check "Gateway Rate-Limiting Timeseries present" ($PanelTitles -contains "Gateway Rate-Limiting (429 Drops) & 5xx Server Errors")
    } catch {
        Assert-Check "Dashboard JSON parsing" $false $_.Exception.Message
    }
}

# ==============================================================================
# SECTION 5: TLS & Ingress Architecture Hardening
# ==============================================================================
Write-Host ""
Write-Host "[5/5] Validating TLS Infrastructure & Ingress Manifests..." -ForegroundColor Cyan

$GatewayAppYml = Join-Path $WorkspaceRoot "backend/api-gateway/src/main/resources/application.yml"
$IngressTlsYaml = Join-Path $WorkspaceRoot "infra/k8s/ingress-tls.yaml"
$CertGenPs1 = Join-Path $WorkspaceRoot "scripts/generate_tls_certs.ps1"
$CertGenSh = Join-Path $WorkspaceRoot "scripts/generate_tls_certs.sh"

Assert-Check "application.yml has server.ssl block" ((Get-Content $GatewayAppYml -Raw) -match "ssl:\s*\n\s*enabled:\s*\$\{SSL_ENABLED:false\}")
Assert-Check "infra/k8s/ingress-tls.yaml exists" (Test-Path $IngressTlsYaml)

$IngressContent = if (Test-Path $IngressTlsYaml) { Get-Content $IngressTlsYaml -Raw } else { "" }
Assert-Check "Ingress defines TLS secret 'coobs-tls-secret'" ($IngressContent -match "secretName:\s*coobs-tls-secret")
Assert-Check "Ingress defines SecretProviderClass for Key Vault" ($IngressContent -match "kind:\s*SecretProviderClass")
Assert-Check "Ingress enables strict TLS 1.2/1.3 ciphers" ($IngressContent -match "TLSv1\.2 TLSv1\.3")

Assert-Check "generate_tls_certs.ps1 exists" (Test-Path $CertGenPs1)
Assert-Check "generate_tls_certs.sh exists" (Test-Path $CertGenSh)

# Optional Live Container Verification
if ($CheckLiveContainers) {
    Write-Host ""
    Write-Host "[*] Checking live container endpoints..." -ForegroundColor Cyan
    try {
        $PromResp = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/targets" -Method Get -TimeoutSec 3 -ErrorAction SilentlyContinue
        if ($PromResp -and $PromResp.status -eq "success") {
            Assert-Check "Live Prometheus is reachable and healthy" $true "Discovered $($PromResp.data.activeTargets.Count) active targets"
        } else {
            Write-Host "  [SKIP] Prometheus container not running on port 9090" -ForegroundColor Gray
        }
    } catch {
        Write-Host "  [SKIP] Prometheus container not reachable on port 9090" -ForegroundColor Gray
    }
}

# ==============================================================================
# SUMMARY REPORT
# ==============================================================================
Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "                     VERIFICATION SUMMARY                        " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Passed checks: $Passed" -ForegroundColor Green
Write-Host "  Failed checks: $Failed" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "=================================================================" -ForegroundColor Cyan

if ($Failed -eq 0) {
    Write-Host "SUCCESS: All FIN-807 Production Hardening requirements verified!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "WARNING: One or more checks failed. Please review the output above." -ForegroundColor Yellow
    exit 1
}
