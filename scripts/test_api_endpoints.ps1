<#
.SYNOPSIS
    Automated API Suite Runner (CLI equivalent of Postman Collection)
.DESCRIPTION
    Executes automated tests across all 6 microservices through Spring Cloud Gateway (Port 8080).
    Validates Auth, Accounts, Ledger, Idempotency, Kafka Notifications, and Forensic Audits.
#>

[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Core Retail Banking - Automated API Test Runner (Postman Suite) " -ForegroundColor Cyan
Write-Host " Target Gateway: $GatewayUrl                                     " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$passed = 0
$total = 0

function Run-Test([string]$name, [scriptblock]$action) {
    $script:total++
    Write-Host "`n[$($script:total)] Testing: $name..." -ForegroundColor Yellow
    try {
        & $action
        Write-Host "  [PASS] $name" -ForegroundColor Green
        $script:passed++
    } catch {
        Write-Host "  [FAIL] $($name): $_" -ForegroundColor Red
    }
}

# 1. Healthcheck
Run-Test "Gateway Health (/actuator/health)" {
    $res = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -Method Get
    if ($res.status -ne "UP") { throw "Expected status UP, got $($res.status)" }
}

# 2. The routing table is not published on the public port
Run-Test "Gateway Routes Not Exposed (/actuator/gateway/routes)" {
    try {
        Invoke-RestMethod -Uri "$GatewayUrl/actuator/gateway/routes" -Method Get | Out-Null
    } catch {
        $code = [int]$_.Exception.Response.StatusCode
        if ($code -eq 404) { return }
        throw "Expected 404, got $code"
    }
    throw "Expected 404, but the gateway listed its routes"
}

# 3. Customer Login
$customerToken = $null
Run-Test "Customer Authentication (/api/v1/auth/customers/login)" {
    $body = @{ username = "john_doe"; password = "Password123!" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/customers/login" -Method Post -ContentType "application/json" -Body $body
    if (-not $res.authData.accessToken) { throw "No accessToken returned" }
    $script:customerToken = $res.authData.accessToken
}

# 4. Staff Login
$staffToken = $null
Run-Test "Staff Authentication (/api/v1/auth/staff/login)" {
    $body = @{ username = "admin"; password = "Password123!" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/staff/login" -Method Post -ContentType "application/json" -Body $body
    if (-not ($res.authData.roles -contains "ROLE_ADMIN")) { throw "Missing ROLE_ADMIN" }
    $script:staffToken = $res.authData.accessToken
}

# 5. Token Profile (/auth/token/me)
Run-Test "User Profile Extraction (/api/v1/auth/token/me)" {
    $headers = @{ "Authorization" = "Bearer $script:customerToken" }
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/token/me" -Method Get -Headers $headers
    if ($res.username -ne "john_doe") { throw "Expected john_doe, got $($res.username)" }
}

# 6. Customer Accounts
$srcAccount = 1
Run-Test "Customer Accounts (/api/v1/accounts/my-accounts)" {
    $headers = @{ "Authorization" = "Bearer $script:customerToken" }
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/accounts/my-accounts" -Method Get -Headers $headers
    if ($res.Count -lt 1) { throw "Customer has no accounts" }
    $script:srcAccount = $res[0].accountId
}

# 7. Balance Inspection
Run-Test "Cached Balance Lookup (/api/v1/ledger/balance/$srcAccount)" {
    $headers = @{ "Authorization" = "Bearer $script:customerToken" }
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/ledger/balance/$script:srcAccount" -Method Get -Headers $headers
    if ($null -eq $res.availableBalance) { throw "No availableBalance property returned" }
}

# 8. Atomic Double-Entry Fund Transfer
$idempKey = "cli_idemp_" + [guid]::NewGuid().ToString()
$refNo = "REF-CLI-" + (Get-Date).Ticks
Run-Test "Execute Transfer (/api/v1/ledger/transfer)" {
    $headers = @{
        "Authorization"   = "Bearer $script:customerToken"
        "Idempotency-Key" = $script:idempKey
    }
    $body = @{
        sourceAccountId      = $script:srcAccount
        destinationAccountId = 2
        amount               = 100.0000
        referenceNo          = $script:refNo
        currency             = "PHP"
        remarks              = "PowerShell CLI Test Transfer"
    } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/ledger/transfer" -Method Post -Headers $headers -ContentType "application/json" -Body $body
    if ($res.status -ne "COMPLETED") { throw "Expected COMPLETED status, got $($res.status)" }
}

# 9. Idempotency Replay
Run-Test "Idempotent Replay Verification (Same Idempotency-Key)" {
    $headers = @{
        "Authorization"   = "Bearer $script:customerToken"
        "Idempotency-Key" = $script:idempKey
    }
    $body = @{
        sourceAccountId      = $script:srcAccount
        destinationAccountId = 2
        amount               = 100.0000
        referenceNo          = $script:refNo
        currency             = "PHP"
    } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/ledger/transfer" -Method Post -Headers $headers -ContentType "application/json" -Body $body
    if ($res.status -ne "COMPLETED") { throw "Replay failed, got $($res.status)" }
}

# 10. Overdraft Rejection
Run-Test "Negative Invariant Overdraft Guard (422 INSUFFICIENT_FUNDS)" {
    $headers = @{
        "Authorization"   = "Bearer $script:customerToken"
        "Idempotency-Key" = [guid]::NewGuid().ToString()
    }
    $body = @{
        sourceAccountId      = $script:srcAccount
        destinationAccountId = 2
        amount               = 99999999.0000
        referenceNo          = "REF-OVERDRAFT-" + (Get-Date).Ticks
        currency             = "PHP"
    } | ConvertTo-Json
    try {
        $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/ledger/transfer" -Method Post -Headers $headers -ContentType "application/json" -Body $body
        throw "Expected HTTP 422 error, but request succeeded!"
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -ne 422) {
            throw "Expected HTTP 422, got $($_.Exception.Response.StatusCode.value__)"
        }
    }
}

# 11. Kafka Event-Driven Notifications
Run-Test "Kafka Consumer In-App Notifications (/api/v1/notifications/my-notifications)" {
    $headers = @{ "Authorization" = "Bearer $script:customerToken" }
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/notifications/my-notifications" -Method Get -Headers $headers
    $items = if ($res.content) { $res.content } else { $res }
    if ($null -eq $items) { throw "No notifications structure returned" }
}

# 12. Forensic Audit Statement
Run-Test "PostgreSQL Immutable Audit Statement (/api/v1/audit/accounts/$srcAccount/statement)" {
    $headers = @{ "Authorization" = "Bearer $script:customerToken" }
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/audit/accounts/$script:srcAccount/statement" -Method Get -Headers $headers
    $items = if ($res.content) { $res.content } else { $res }
    if ($items.Count -lt 1) { throw "No audit records found for account" }
}

# 13. Cryptographic SHA-256 Hash Chain Verification
Run-Test "Cryptographic SHA-256 Hash Chain Verification (/api/v1/audit/verify-chain/$srcAccount)" {
    $headers = @{ "Authorization" = "Bearer $script:customerToken" }
    $res = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/audit/verify-chain/$script:srcAccount" -Method Get -Headers $headers
    if ($res.isChainIntact -ne $true) { throw "Hash chain integrity compromised: $($res.message)" }
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host " Test Execution Completed: $passed / $total Passed" -ForegroundColor $(if ($passed -eq $total) { "Green" } else { "Yellow" })
Write-Host "=================================================================" -ForegroundColor Cyan
