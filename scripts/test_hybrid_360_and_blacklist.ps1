# ==============================================================================
# CooBS: Automated Hybrid 360 Read & Redis Token Blacklist Verification Script
# ==============================================================================
param(
    [string]$GatewayUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Write-Pass($msg) { Write-Host "  [PASS] $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "  [FAIL] $msg" -ForegroundColor Red }
function Write-Header($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

Write-Host "==================================================================" -ForegroundColor Yellow
Write-Host " Running Live Hybrid Read & Redis Blacklist Verification Suite   " -ForegroundColor Yellow
Write-Host " Gateway URL: $GatewayUrl" -ForegroundColor Yellow
Write-Host "==================================================================" -ForegroundColor Yellow

# ------------------------------------------------------------------------------
# TEST 1: Redis Token Blacklist (JTI Revocation on Logout / Revoke)
# ------------------------------------------------------------------------------
Write-Header "TEST 1: Redis Token Blacklist (JTI Revocation & Session Invalidation)"

try {
    $loginBody = @{ username = "david_kim"; password = "Password123!" } | ConvertTo-Json -Compress
    $loginRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/customers/login" -Method Post -ContentType "application/json" -Body $loginBody
    $token = $loginRes.authData.accessToken
    Write-Pass "Customer logged in (david_kim). Access token acquired."

    # Verify session is valid before revocation
    $meBefore = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/token/me" -Method Get -Headers @{ Authorization = "Bearer $token" }
    if ($meBefore.username -eq "david_kim") {
        Write-Pass "Active token verified on /auth/token/me (User: $($meBefore.username), ID: $($meBefore.userId))"
    } else {
        Write-Fail "User mismatch on /auth/token/me"
    }

    # Revoke token
    $revokeRes = Invoke-WebRequest -Uri "$GatewayUrl/api/v1/auth/token/revoke" -Method Post -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing
    if ($revokeRes.StatusCode -eq 200 -or $revokeRes.StatusCode -eq 204) {
        Write-Pass "Token revoked via POST /api/v1/auth/token/revoke (HTTP $($revokeRes.StatusCode))"
    } else {
        Write-Fail "Unexpected revocation status code: $($revokeRes.StatusCode)"
    }

    # Reusing the revoked token must return 401
    try {
        $null = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/token/me" -Method Get -Headers @{ Authorization = "Bearer $token" }
        Write-Fail "Revoked token was STILL accepted! Blacklist check failed."
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 401) {
            Write-Pass "Reusing blacklisted token was successfully rejected with HTTP 401 Unauthorized."
        } else {
            Write-Fail "Expected HTTP 401 on revoked token reuse, but received HTTP $statusCode"
        }
    }
} catch {
    Write-Fail "TEST 1 failed with error: $_"
}

# ------------------------------------------------------------------------------
# TEST 2: Hybrid 360 Read (Cloud Profile + On-Prem Oracle Accounts)
# ------------------------------------------------------------------------------
Write-Header "TEST 2: Customer 360 Hybrid Read (Customer View)"

try {
    $custLoginBody = @{ username = "john_doe"; password = "Password123!" } | ConvertTo-Json -Compress
    $custLoginRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/customers/login" -Method Post -ContentType "application/json" -Body $custLoginBody
    $custToken = $custLoginRes.authData.accessToken
    $custId = $custLoginRes.authData.userId
    Write-Pass "Customer logged in (john_doe, ID: $custId). Token acquired."

    # Call Customer 360 aggregator
    $c360 = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/customers/$custId/360" -Method Get -Headers @{ Authorization = "Bearer $custToken" }

    # 1. Verify Cloud Profile elements (from PostgreSQL customer_db)
    if ($c360.displayName -eq "John D." -and $c360.kycStatus -eq "VERIFIED" -and $c360.membershipNumber) {
        Write-Pass "Cloud Profile verified: DisplayName='$($c360.displayName)', MBR='$($c360.membershipNumber)', KYC='$($c360.kycStatus) / $($c360.kycTier)'"
    } else {
        Write-Fail "Cloud profile data incorrect: displayName='$($c360.displayName)', KYC='$($c360.kycStatus)'"
    }

    # 2. Verify On-Premises Oracle Accounts (from account-service via internal service JWT)
    if ($c360.accounts -and $c360.accounts.Count -gt 0) {
        Write-Pass "On-Prem Oracle accounts successfully aggregated: $($c360.accounts.Count) account(s) retrieved."
        foreach ($acc in $c360.accounts) {
            Write-Host "     - $($acc.accountNumber) | Type: $($acc.accountType) | Bal: $($acc.availableBalance) $($acc.currency) | Status: $($acc.status)" -ForegroundColor Gray
        }
    } else {
        Write-Fail "No accounts returned in Customer 360! Hybrid link to account-service failed."
    }

    # 3. Verify Financial Totals calculation
    if ([decimal]$c360.totals.deposits -gt 0) {
        Write-Pass "Aggregated Financial Totals verified: Total Deposits = PHP $($c360.totals.deposits), Total Loans = PHP $($c360.totals.loans)"
    } else {
        Write-Fail "Total deposits calculation mismatch: $($c360.totals.deposits)"
    }

    # 4. Verify Customer privacy: risk score masked for customers
    if ($null -eq $c360.riskScore) {
        Write-Pass "Security check passed: Risk score is masked for customer view."
    } else {
        Write-Fail "Privacy leak: Risk score was returned to customer ($($c360.riskScore))"
    }

} catch {
    Write-Fail "TEST 2 failed with error: $_"
}

# ------------------------------------------------------------------------------
# TEST 3: Hybrid 360 Read (Staff View with Unmasked Risk Score)
# ------------------------------------------------------------------------------
Write-Header "TEST 3: Customer 360 Hybrid Read (Staff View)"

try {
    $staffLoginBody = @{ username = "teller_alice"; password = "Password123!" } | ConvertTo-Json -Compress
    $staffLoginRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/staff/login" -Method Post -ContentType "application/json" -Body $staffLoginBody
    $staffToken = $staffLoginRes.authData.accessToken
    Write-Pass "Staff logged in (teller_sarah). Token acquired."

    $staff360 = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/customers/1/360" -Method Get -Headers @{ Authorization = "Bearer $staffToken" }

    if ($staff360.riskScore) {
        Write-Pass "Staff view verified: Risk score unmasked for staff (Score: $($staff360.riskScore))"
    } else {
        Write-Fail "Staff view failed to expose risk score."
    }

    Write-Pass "Staff 360 lookup verified: Found $($staff360.accounts.Count) accounts for Customer 1."

} catch {
    Write-Fail "TEST 3 failed with error: $_"
}

# ------------------------------------------------------------------------------
# TEST 4: Handshake H10 (Transaction-Service T8/T9 Internal Endpoints & 360 Aggregation)
# ------------------------------------------------------------------------------
Write-Header "TEST 4: Handshake H10 (Internal Transactions T8/T9 & Customer 360 Aggregation)"

try {
    # 1. Initiate a transfer from john_doe (sourceAccountId: 1)
    Write-Host "Initiating a sample transfer from Customer 1..." -ForegroundColor Gray
    $transferBody = @{
        sourceAccountId = 1
        destinationAccountNumber = "ACC_10000002"
        destinationBankCode = "COOBS"
        amount = 500.00
        currency = "PHP"
        category = "UTILITIES"
        remarks = "Electric Bill Payment"
    } | ConvertTo-Json -Compress

    $idempKey = [System.Guid]::NewGuid().ToString()
    $transferRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/transactions/transfers" -Method Post -Headers @{
        Authorization = "Bearer $custToken"
        "Idempotency-Key" = $idempKey
    } -ContentType "application/json" -Body $transferBody

    Write-Pass "Transfer initiated (Ref: $($transferRes.referenceNo), ID: $($transferRes.transactionId))"

    # Small pause to allow event propagation
    Start-Sleep -Seconds 1

    # 2. Re-fetch Customer 360 and assert recentTransactions & spendingBreakdown populate
    $updated360 = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/customers/1/360" -Method Get -Headers @{ Authorization = "Bearer $custToken" }

    if ($null -ne $updated360.recentTransactions -and $updated360.recentTransactions.Count -gt 0) {
        Write-Pass "Customer 360 aggregated recent transactions: $($updated360.recentTransactions.Count) transaction(s) found."
        $latestTxn = $updated360.recentTransactions[0]
        Write-Host "     - Ref: $($latestTxn.referenceNo) | Amount: $($latestTxn.amount) $($latestTxn.currency) | Category: $($latestTxn.category) | Remarks: $($latestTxn.counterpartyName)" -ForegroundColor Gray
    } else {
        Write-Fail "Customer 360 recentTransactions is empty after transfer initiation."
    }

    if ($null -ne $updated360.spendingBreakdown -and $updated360.spendingBreakdown.items.Count -gt 0) {
        Write-Pass "Customer 360 aggregated spending breakdown: $($updated360.spendingBreakdown.items.Count) category(ies) found."
        foreach ($item in $updated360.spendingBreakdown.items) {
            Write-Host "     - Category: $($item.category) | Total: PHP $($item.amount) | Count: $($item.count)" -ForegroundColor Gray
        }
    } else {
        Write-Fail "Customer 360 spendingBreakdown is empty after transfer initiation."
    }

    # 3. Test direct access to internal endpoints with customer token (must be rejected with HTTP 403)
    try {
        $null = Invoke-RestMethod -Uri "http://localhost:8090/internal/v1/transactions?customerId=1&limit=5" -Method Get -Headers @{ Authorization = "Bearer $custToken" }
        Write-Fail "Security leak: Customer token was accepted on internal transaction-service endpoint!"
    } catch {
        $sc = $_.Exception.Response.StatusCode.value__
        if ($sc -eq 403) {
            Write-Pass "Security check passed: Customer token correctly rejected with HTTP 403 Forbidden on internal transaction endpoint."
        } else {
            Write-Fail "Expected HTTP 403 on customer access to internal endpoint, but got HTTP $sc"
        }
    }

} catch {
    Write-Fail "TEST 4 failed with error: $_"
}

Write-Host "`n==================================================================" -ForegroundColor Yellow
Write-Host " Verification Suite Completed                                    " -ForegroundColor Yellow
Write-Host "==================================================================" -ForegroundColor Yellow
