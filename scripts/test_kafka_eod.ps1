<#
.SYNOPSIS
    Handshake H11 Kafka Verification Test for EOD Batch Pipeline
.DESCRIPTION
    Triggers EOD batch run via API Gateway, waits for pipeline completion,
    and consumes messages from Kafka topic 'batch.eod.step.completed.v1'.
    Validates payload structure and date serialization against API contracts.
#>

param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$BusinessDate = "2026-10-05"
)

$ErrorActionPreference = "Continue"
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " Handshake H11: Kafka EOD Step Completed Verification Suite       " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

# 1. Staff Login
Write-Host "`n[Step 1/4] Authenticating operations staff via API Gateway..." -ForegroundColor Yellow
$loginPayload = @{
    username = "admin"
    password = "Password123!"
} | ConvertTo-Json

$authRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/staff/login" -Method Post -ContentType "application/json" -Body $loginPayload
$token = $authRes.authData.accessToken
if (-not $token) {
    throw "Failed to acquire JWT token for admin"
}
Write-Host "  [PASS] Authenticated successfully. Roles: $($authRes.authData.roles -join ', ')" -ForegroundColor Green

$headers = @{
    "Authorization" = "Bearer $token"
}

# 2. Trigger Batch Run
Write-Host "`n[Step 2/4] Triggering EOD Batch Pipeline for Business Date: $BusinessDate..." -ForegroundColor Yellow
$triggerPayload = @{
    businessDate = $BusinessDate
} | ConvertTo-Json

$triggerRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/batch/eod/trigger" -Method Post -Headers $headers -ContentType "application/json" -Body $triggerPayload
$runId = $triggerRes.batchRunId
Write-Host "  [PASS] Batch Run #$runId queued with status: $($triggerRes.status)" -ForegroundColor Green

# 3. Poll for completion
Write-Host "`n[Step 3/4] Polling Batch Run #$runId status (SLA: 5-8 seconds)..." -ForegroundColor Yellow
$timeoutSeconds = 20
$startTime = [DateTime]::UtcNow
$completedRun = $null

while (([DateTime]::UtcNow - $startTime).TotalSeconds -lt $timeoutSeconds) {
    Start-Sleep -Seconds 1
    $statusRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/batch/eod/runs/$runId" -Method Get -Headers $headers
    Write-Host "  Current Status: $($statusRes.status) (Steps: $($statusRes.steps.Count)/6)" -ForegroundColor DarkGray
    if ($statusRes.status -eq "COMPLETED" -or $statusRes.status -eq "HALTED") {
        $completedRun = $statusRes
        break
    }
}

if (-not $completedRun) {
    throw "Batch Run #$runId timed out after $timeoutSeconds seconds"
}

Write-Host "  [PASS] Batch Run #$runId finished with status: $($completedRun.status)" -ForegroundColor Green

# 4. Consume from Kafka topic
Write-Host "`n[Step 4/4] Consuming events from Kafka topic 'batch.eod.step.completed.v1'..." -ForegroundColor Yellow

$consumerOutput = docker exec -i kafka-broker /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server localhost:9092 `
    --topic batch.eod.step.completed.v1 `
    --from-beginning `
    --timeout-ms 5000 2>&1

$lines = $consumerOutput -split "`r?`n" | Where-Object { $_ -match "^\{" }

if ($lines.Count -eq 0) {
    Write-Host "  [FAIL] No JSON events found on topic batch.eod.step.completed.v1!" -ForegroundColor Red
    Write-Host "Raw output: $consumerOutput"
    exit 1
}

Write-Host "  Found $($lines.Count) event(s) in topic. Validating payload structure..." -ForegroundColor Cyan

$runEvents = @()
foreach ($line in $lines) {
    try {
        $evt = $line | ConvertFrom-Json
        if ($evt.batchRunId -eq $runId) {
            $runEvents += $evt
        }
    } catch {
        # ignore non-json
    }
}

Write-Host "`nEvents captured for BatchRun #${runId}: $($runEvents.Count)/6" -ForegroundColor Cyan

$expectedSteps = @(
    "INTEREST_ACCRUAL",
    "SERVICE_FEES",
    "STANDING_INSTRUCTIONS",
    "LOAN_AMORTIZATION",
    "GL_RECONCILIATION",
    "AUDIT_SEAL"
)

$allValid = $true
foreach ($expected in $expectedSteps) {
    $matching = $runEvents | Where-Object { $_.stepName -eq $expected }
    if ($matching) {
        $dateType = $matching.businessDate.GetType().Name
        Write-Host "  [PASS] Step $($matching.stepNo): $($matching.stepName)" -ForegroundColor Green
        Write-Host "         Status: $($matching.status) | Items: $($matching.itemsProcessed) | Amount: $($matching.totalAmount) | Duration: $($matching.durationMs)ms" -ForegroundColor DarkCyan
        Write-Host "         businessDate: $($matching.businessDate) (Type: $dateType)" -ForegroundColor DarkGray
        
        # Verify date is formatted string yyyy-MM-dd
        if ($matching.businessDate -notmatch "^\d{4}-\d{2}-\d{2}$") {
            Write-Host "  [FAIL] businessDate is not in YYYY-MM-DD format! Got: $($matching.businessDate)" -ForegroundColor Red
            $allValid = $false
        }
    } else {
        Write-Host "  [FAIL] Missing Kafka event for step: $expected" -ForegroundColor Red
        $allValid = $false
    }
}

Write-Host ""
if ($allValid -and $runEvents.Count -ge 6) {
    Write-Host "==================================================================" -ForegroundColor Green
    Write-Host " ALL KAFKA EVENTS VERIFIED - HANDSHAKE H11 100% COMPLIANT         " -ForegroundColor Green
    Write-Host "==================================================================" -ForegroundColor Green
} else {
    Write-Host "==================================================================" -ForegroundColor Red
    Write-Host " KAFKA VERIFICATION FAILED                                        " -ForegroundColor Red
    Write-Host "==================================================================" -ForegroundColor Red
    exit 1
}
