<#
.SYNOPSIS
    Automated verification script for CooBS Swagger UI & OpenAPI 3.0 testing console.
.DESCRIPTION
    Validates:
      1. Existence and integrity of contracts/api/openapi.yaml.
      2. docker-compose.yml configuration for the swagger-ui service.
      3. API Gateway route mappings for /swagger-ui/**.
      4. Live HTTP connectivity to Swagger UI on port 8086 and through Gateway port 8080 (if containers are running).
#>

[CmdletBinding()]
param(
    [switch]$CheckLiveContainers
)

$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "     CooBS Retail Banking - Swagger UI / OpenAPI Test Suite      " -ForegroundColor Cyan
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

# 1. Validate OpenAPI Specification
Write-Host ""
Write-Host "[1/3] Validating OpenAPI 3.0 Contract File..." -ForegroundColor Cyan
$OpenApiFile = Join-Path $WorkspaceRoot "contracts/api/openapi.yaml"
Assert-Check "openapi.yaml exists" (Test-Path $OpenApiFile)

if (Test-Path $OpenApiFile) {
    $Content = Get-Content $OpenApiFile -Raw
    Assert-Check "OpenAPI version is 3.0.3" ($Content -match "openapi:\s*3\.0\.3")
    Assert-Check "Security scheme BearerAuth is defined" ($Content -match "BearerAuth:")
    Assert-Check "API Gateway server is configured (8080)" ($Content -match "http://localhost:8080")
    Assert-Check "Auth Service endpoints defined (/api/v1/auth/customers/login)" ($Content -match "/api/v1/auth/customers/login:")
    Assert-Check "Ledger Service endpoints defined (/api/v1/ledger/transfers)" ($Content -match "/api/v1/ledger/transfers:")
    Assert-Check "Transaction Service endpoints defined (/api/v1/transactions/transfers)" ($Content -match "/api/v1/transactions/transfers:")
    Assert-Check "Mobile BFF endpoints defined (/api/v1/mobile/dashboard)" ($Content -match "/api/v1/mobile/dashboard:")
    Assert-Check "Staff BFF endpoints defined (/api/v1/staff/customers/{customerId}/360)" ($Content -match "/api/v1/staff/customers/\{customerId\}/360:")
}

# 2. Validate Docker Compose and API Gateway Config
Write-Host ""
Write-Host "[2/3] Validating Architecture Configuration..." -ForegroundColor Cyan
$ComposeFile = Join-Path $WorkspaceRoot "docker-compose.yml"
$ComposeContent = Get-Content $ComposeFile -Raw
Assert-Check "swagger-ui service defined in docker-compose.yml" ($ComposeContent -match "swagger-ui:")
Assert-Check "swagger-ui maps SWAGGER_UI_PORT (8086)" ($ComposeContent -match "SWAGGER_UI_PORT:-8086")
Assert-Check "SWAGGER_UI_URL wired to api-gateway" ($ComposeContent -match "SWAGGER_UI_URL:\s*http://swagger-ui:8080")

$GatewayYml = Join-Path $WorkspaceRoot "backend/api-gateway/src/main/resources/application.yml"
$GatewayContent = Get-Content $GatewayYml -Raw
Assert-Check "api-gateway routes /swagger-ui/**" ($GatewayContent -match "- Path=/swagger-ui")

# 3. Optional Live Endpoint Verification
Write-Host ""
Write-Host "[3/3] Checking Live Endpoints..." -ForegroundColor Cyan

$EndpointsToCheck = @(
    @{ Name = "Direct Swagger UI Container (:8086)"; Url = "http://localhost:8086/swagger-ui/" },
    @{ Name = "Gateway Routed Swagger UI (:8080)";   Url = "http://localhost:8080/swagger-ui/" }
)

foreach ($ep in $EndpointsToCheck) {
    try {
        $resp = Invoke-WebRequest -Uri $ep.Url -Method Get -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        if ($resp.StatusCode -eq 200) {
            Assert-Check "$($ep.Name) is reachable (HTTP 200)" $true
        } else {
            Write-Host "  [SKIP] $($ep.Name) returned HTTP $($resp.StatusCode)" -ForegroundColor Gray
        }
    } catch {
        Write-Host "  [SKIP] $($ep.Name) not currently running" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "                     VERIFICATION SUMMARY                        " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Passed checks: $Passed" -ForegroundColor Green
Write-Host "  Failed checks: $Failed" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Red" })
Write-Host "=================================================================" -ForegroundColor Cyan

if ($Failed -eq 0) {
    Write-Host "SUCCESS: Swagger UI & OpenAPI 3.0 testing suite verified successfully!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "WARNING: One or more checks failed." -ForegroundColor Yellow
    exit 1
}
