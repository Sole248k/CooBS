# ==============================================================================
# run_postman_e2e.ps1: Automated Postman Newman E2E Regression Runner
# Role: Day 5 CI/CD Checkpoint & Platform E2E Regression (11 Services)
# Reference: DEV8_DEVOPS_WEEKLY_PLAN.md (Task 5.3)
# ==============================================================================
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$CollectionPath = "postman/e2e/CooBS_End_To_End.postman_collection.json",
    [string]$EnvironmentPath = "postman/CooBS_Local.postman_environment.json",
    [string]$OutputDir = "test-results/postman",
    [switch]$UseDocker = $false,
    [switch]$SkipHealthCheck = $false
)

$ErrorActionPreference = "Stop"

# Resolve repo root directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
Set-Location $RootDir

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " CooBS Platform - Postman Newman E2E Test Suite Execution        " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Target Gateway URL  : $BaseUrl" -ForegroundColor Yellow
Write-Host "Collection File     : $CollectionPath" -ForegroundColor Yellow
Write-Host "Environment File    : $EnvironmentPath" -ForegroundColor Yellow
Write-Host "Report Output Dir   : $OutputDir" -ForegroundColor Yellow
Write-Host ""

# Verify files exist
if (-not (Test-Path $CollectionPath)) {
    Write-Error "Postman collection not found at: $CollectionPath"
    exit 1
}
if (-not (Test-Path $EnvironmentPath)) {
    Write-Error "Postman environment not found at: $EnvironmentPath"
    exit 1
}

# Create output directory
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

# Pre-flight Health Check
if (-not $SkipHealthCheck) {
    Write-Host "[1/3] Checking API Gateway connectivity..." -ForegroundColor Cyan
    try {
        $HealthUrl = "$BaseUrl/actuator/health"
        $Response = Invoke-RestMethod -Uri $HealthUrl -Method Get -TimeoutSec 5 -ErrorAction Stop
        if ($Response.status -eq "UP") {
            Write-Host "  [PASS] API Gateway is healthy (status: UP)" -ForegroundColor Green
        } else {
            Write-Host "  [WARN] API Gateway returned status: $($Response.status)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  [WARN] Could not connect to API Gateway at $BaseUrl" -ForegroundColor Yellow
        Write-Host "         Ensure containers are running: 'docker compose --profile app --profile expansion up -d'" -ForegroundColor Gray
        Write-Host "         Proceeding with Newman execution..." -ForegroundColor Gray
    }
} else {
    Write-Host "[1/3] Skipping pre-flight health check as requested." -ForegroundColor Gray
}

# Determine Newman execution method
Write-Host "`n[2/3] Resolving Newman execution runtime..." -ForegroundColor Cyan
$RunnerMode = "none"

if (-not $UseDocker) {
    if (Get-Command newman -ErrorAction SilentlyContinue) {
        $RunnerMode = "global"
        Write-Host "  [INFO] Using globally installed Newman ($(Get-Command newman | Select-Object -ExpandProperty Source))" -ForegroundColor Green
    } elseif (Get-Command npx -ErrorAction SilentlyContinue) {
        $RunnerMode = "npx"
        Write-Host "  [INFO] Using npx newman runner" -ForegroundColor Green
    }
}

if ($RunnerMode -eq "none" -or $UseDocker) {
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $RunnerMode = "docker"
        Write-Host "  [INFO] Using Docker container runner (postman/newman:alpine)" -ForegroundColor Green
    } else {
        Write-Error "Neither newman, npx, nor docker is available to execute Postman tests."
        exit 1
    }
}

# Execution
Write-Host "`n[3/3] Executing Postman End-to-End Test Suite..." -ForegroundColor Cyan
$JunitReport = Join-Path $OutputDir "postman-e2e-junit.xml"
$HtmlReport = Join-Path $OutputDir "postman-e2e-report.html"

$StartTime = Get-Date

if ($RunnerMode -eq "global") {
    newman run $CollectionPath `
        -e $EnvironmentPath `
        --env-var "baseUrl=$BaseUrl" `
        --reporters cli,junit `
        --reporter-junit-export $JunitReport `
        --bail false
    $ExitCode = $LASTEXITCODE
} elseif ($RunnerMode -eq "npx") {
    & npx -y newman run $CollectionPath `
        -e $EnvironmentPath `
        --env-var "baseUrl=$BaseUrl" `
        --reporters cli,junit `
        --reporter-junit-export $JunitReport `
        --bail false
    $ExitCode = $LASTEXITCODE
} elseif ($RunnerMode -eq "docker") {
    $WorkspaceDir = (Get-Location).Path
    $DockerCmd = "docker run --rm --network host -v `"${WorkspaceDir}:/workspace`" -w /workspace postman/newman:alpine run `"$CollectionPath`" -e `"$EnvironmentPath`" --env-var `"baseUrl=$BaseUrl`" --reporters cli,junit --reporter-junit-export `"$JunitReport`" --bail false"
    Invoke-Expression $DockerCmd
    $ExitCode = $LASTEXITCODE
}

$Duration = [math]::Round(((Get-Date) - $StartTime).TotalSeconds, 2)

Write-Host "`n=================================================================" -ForegroundColor Cyan
if ($ExitCode -eq 0) {
    Write-Host " POSTMAN E2E RUN PASSED ($Duration seconds)" -ForegroundColor Green
    Write-Host " JUnit Report saved to: $JunitReport" -ForegroundColor Gray
} else {
    Write-Host " POSTMAN E2E RUN FAILED with exit code $ExitCode ($Duration seconds)" -ForegroundColor Red
    Write-Host " Check results at: $JunitReport" -ForegroundColor Gray
}
Write-Host "=================================================================" -ForegroundColor Cyan

exit $ExitCode
