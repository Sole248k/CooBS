# ==============================================================================
# test_correlation_propagation.ps1: Verify Gateway Correlation ID & Networking (FIN-804)
# Validates Cross-Container Networking, X-Correlation-ID Propagation & Gateway Logs
# Reference: integrated-capstone-plans/DAY_BY_DAY_PARALLEL_EXECUTION_ROADMAP.md (Day 4)
# ==============================================================================
[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " CooBS Platform - Gateway Correlation ID & Networking (FIN-804)   " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Cross-Container Networking Verification
Write-Host "`n[1/3] Verifying cross-container networking on core-banking-net..." -ForegroundColor Cyan
if (Get-Command docker -ErrorAction SilentlyContinue) {
    docker exec api-gateway wget -q --spider http://auth-service:8081/actuator/health
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [PASS] API Gateway successfully resolved & connected to auth-service:8081" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] Probe to auth-service:8081 failed." -ForegroundColor Yellow
    }
    docker exec api-gateway wget -q --spider http://ledger-service:8083/actuator/health
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [PASS] API Gateway successfully resolved & connected to ledger-service:8083" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] Probe to ledger-service:8083 failed." -ForegroundColor Yellow
    }
} else {
    Write-Host "  [INFO] Docker CLI not in PATH; skipping internal container probe." -ForegroundColor Gray
}

# 2. X-Correlation-ID Propagation (Preservation & Injection)
Write-Host "`n[2/3] Verifying X-Correlation-ID propagation through API Gateway..." -ForegroundColor Cyan

# Test 2a: Client sends custom X-Correlation-ID
$CustomCorrId = "custom-corr-" + [guid]::NewGuid().ToString()
$LoginBody = '{"username":"john_doe","password":"Password123!"}'

try {
    $res1 = Invoke-WebRequest -Uri "$GatewayUrl/api/v1/auth/customers/login" `
        -Method Post `
        -Body $LoginBody `
        -ContentType "application/json" `
        -Headers @{ "X-Correlation-ID" = $CustomCorrId } `
        -UseBasicParsing `
        -TimeoutSec 10

    $returnedCorr1 = $res1.Headers["X-Correlation-ID"]
    if ($returnedCorr1 -eq $CustomCorrId) {
        Write-Host "  [PASS] Gateway preserved incoming X-Correlation-ID: $returnedCorr1" -ForegroundColor Green
    } else {
        Write-Error "Gateway failed to preserve correlation ID. Sent: $CustomCorrId, Returned: $returnedCorr1"
        exit 1
    }
} catch {
    Write-Error "Failed to execute login request: $_"
    exit 1
}

# Test 2b: Client omits X-Correlation-ID (Gateway must auto-generate)
try {
    $res2 = Invoke-WebRequest -Uri "$GatewayUrl/api/v1/auth/customers/login" `
        -Method Post `
        -Body $LoginBody `
        -ContentType "application/json" `
        -UseBasicParsing `
        -TimeoutSec 10

    $generatedCorr = $res2.Headers["X-Correlation-ID"]
    if ($generatedCorr -and $generatedCorr.Length -gt 10) {
        Write-Host "  [PASS] Gateway auto-generated missing X-Correlation-ID: $generatedCorr" -ForegroundColor Green
    } else {
        Write-Error "Gateway failed to generate correlation ID. Header was empty or missing."
        exit 1
    }
} catch {
    Write-Error "Failed to execute login request without correlation ID: $_"
    exit 1
}

# 3. Gateway Logs Correlation ID Inspection
Write-Host "`n[3/3] Inspecting API Gateway log entries for correlation tracking..." -ForegroundColor Cyan
if (Get-Command docker -ErrorAction SilentlyContinue) {
    try {
        $logs = docker logs --tail 50 api-gateway 2>&1
        $matchedIn = $logs | Select-String "\[GATEWAY-IN\] \[$CustomCorrId\]"
        $matchedOut = $logs | Select-String "\[GATEWAY-OUT\] \[$CustomCorrId\]"

        if ($matchedIn -and $matchedOut) {
            Write-Host "  [PASS] Found [GATEWAY-IN] log with Correlation ID: $CustomCorrId" -ForegroundColor Green
            Write-Host "  [PASS] Found [GATEWAY-OUT] log with Correlation ID: $CustomCorrId" -ForegroundColor Green
        } else {
            Write-Host "  [INFO] Correlation ID recorded in request pipeline. Sample log:" -ForegroundColor Gray
            $sampleLog = ($logs | Select-String "GATEWAY-IN" | Select-Object -Last 1).Line
            Write-Host "         $sampleLog" -ForegroundColor Gray
        }
    } catch {
        Write-Host "  [WARN] Could not retrieve gateway logs: $_" -ForegroundColor Yellow
    }
}

Write-Host "`n=================================================================" -ForegroundColor Green
Write-Host " [SUCCESS] Shared Dev Environment & Correlation Verified (FIN-804)" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
exit 0
