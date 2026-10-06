<#
.SYNOPSIS
    CooBS Spring Cloud Gateway Readiness & Health Probe for Mobile App
.DESCRIPTION
    Checks if Spring Cloud Gateway (port 8080) and core routing services are live.
    Provides diagnostic guidance for both offline fixture testing and live emulator runs.
#>

[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " CooBS Gateway Readiness Probe for Mobile Client                 " -ForegroundColor Cyan
Write-Host " Target URL: $GatewayUrl                                         " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$portCheck = Test-NetConnection -ComputerName "localhost" -Port 8080 -WarningAction SilentlyContinue

if (-not $portCheck.TcpTestSucceeded) {
    Write-Host "`n[NOTICE] Port 8080 is NOT open. API Gateway is currently offline." -ForegroundColor Yellow
    Write-Host "-----------------------------------------------------------------" -ForegroundColor Gray
    Write-Host " The mobile app is fully configured for offline development!" -ForegroundColor Green
    Write-Host " Handshake H15 offline fixture mode is active by default." -ForegroundColor Green
    Write-Host ""
    Write-Host " To run mobile app in emulator using verified JSON fixtures:" -ForegroundColor Cyan
    Write-Host "   cd app" -ForegroundColor White
    Write-Host "   flutter run -d <emulator-id>" -ForegroundColor White
    Write-Host ""
    Write-Host " When you are ready to spin up the backend stack:" -ForegroundColor Cyan
    Write-Host "   docker compose up -d" -ForegroundColor White
    Write-Host "-----------------------------------------------------------------" -ForegroundColor Gray
    exit 0
}

Write-Host "`n[Check 1/3] Gateway Actuator Health (/actuator/health)..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get -TimeoutSec 3
    if ($health.status -eq "UP") {
        Write-Host "  [PASS] Gateway is UP." -ForegroundColor Green
    } else {
        Write-Host "  [WARN] Gateway status: $($health.status)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [FAIL] Failed to reach health endpoint: $_" -ForegroundColor Red
}

Write-Host "`n[Check 2/3] Gateway Route Discovery (/actuator/gateway/routes)..." -ForegroundColor Yellow
try {
    $routes = Invoke-RestMethod -Uri "$GatewayUrl/actuator/gateway/routes" -Method Get -TimeoutSec 3
    Write-Host "  [PASS] Discovered $($routes.Count) routes." -ForegroundColor Green
} catch {
    Write-Host "  [WARN] Route discovery endpoint unavailable or protected." -ForegroundColor Yellow
}

Write-Host "`n[Check 3/3] Customer Authentication Probe (/api/v1/auth/customers/login)..." -ForegroundColor Yellow
try {
    $body = @{ username = "john_doe"; password = "Password123!" } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/customers/login" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 3
    if ($login.authData -and $login.authData.accessToken) {
        Write-Host "  [PASS] Auth service live. Customer token retrieved successfully." -ForegroundColor Green
        Write-Host "`n>>> GATEWAY IS LIVE! <<<" -ForegroundColor Green
        Write-Host "To run mobile app against live backend in emulator:" -ForegroundColor Cyan
        Write-Host "  flutter run -d <emulator-id> --dart-define=USE_FIXTURES=false" -ForegroundColor White
    } else {
        Write-Host "  [WARN] Auth returned unexpected payload format." -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [WARN] Auth service not reachable through gateway: $_" -ForegroundColor Yellow
}
