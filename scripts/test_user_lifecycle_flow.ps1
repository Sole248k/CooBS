<#
.SYNOPSIS
    Automated Runner for the End-to-End User Lifecycle Flow (Frozen Contract Aligned)
.DESCRIPTION
    Executes the 8-step customer & account lifecycle flow using validated seed personas:
    1. Staff / Teller Authentication & Session Issuance (teller_alice)
    2. Customer Authentication & JWT Issuance (david_kim)
    3. Teller Opens New Lifecycle Account for Customer (POST /api/v1/accounts)
    4. Customer Submits KYC Information Update Request (POST /api/v1/customers/kyc/update-request)
    5. Staff/Admin Approves KYC Update Request (POST /api/v1/customers/kyc/update-requests/{id}/approve)
    6. Customer Submits Zero-Balance Account Closure Request (POST /api/v1/accounts/{id}/closure-request)
    7. Staff/Admin Approves Account Closure Request (POST /api/v1/accounts/closure-requests/{id}/approve)
    8. Account Lifecycle State Verification (GET /api/v1/accounts/{id} -> Status: CLOSED)
#>

[CmdletBinding()]
param(
    [string]$GatewayUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host " Core Retail Banking - User Lifecycle Flow Verification Suite            " -ForegroundColor Cyan
Write-Host " Target Gateway: $GatewayUrl                                              " -ForegroundColor Cyan
Write-Host " Target Seed:    david_kim (Customer ID: 3) / teller_alice / admin       " -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Cyan

$passed = 0
$total = 8
$report = [System.Collections.Generic.List[PSCustomObject]]::new()

function Record-Step([string]$stepName, [string]$endpoint, [int]$status, [long]$elapsedMs, [string]$details) {
    $report.Add([PSCustomObject]@{
        Step     = $stepName
        Endpoint = $endpoint
        Status   = $status
        Latency  = "$elapsedMs ms"
        Details  = $details
    })
}

function New-CorrId {
    return [Guid]::NewGuid().ToString()
}

# -----------------------------------------------------------------------------
# STEP 1: Teller Authentication
# -----------------------------------------------------------------------------
Write-Host "`n[Step 1/8] Teller logging in with credentials (teller_alice)..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$corrId = New-CorrId
$tellerLogin = @{ username = "teller_alice"; password = "Password123!" } | ConvertTo-Json
$tellerRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/staff/login" -Method Post -ContentType "application/json" -Headers @{ "X-Correlation-ID" = $corrId } -Body $tellerLogin
$sw.Stop()

$tellerToken = $tellerRes.authData.accessToken
if (-not $tellerToken) { throw "Teller login did not return accessToken" }
Write-Host "  [PASS] Teller authenticated successfully. Roles: $($tellerRes.authData.roles -join ', ')" -ForegroundColor Green
Record-Step "1. Teller Login" "POST /api/v1/auth/staff/login" 200 $sw.ElapsedMilliseconds "User: teller_alice"
$passed++

# -----------------------------------------------------------------------------
# STEP 2: Customer Authentication (david_kim)
# -----------------------------------------------------------------------------
Write-Host "`n[Step 2/8] Customer logging in with credentials (david_kim)..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$corrId = New-CorrId
$loginBody = @{ username = "david_kim"; password = "Password123!" } | ConvertTo-Json
$loginRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/customers/login" -Method Post -ContentType "application/json" -Headers @{ "X-Correlation-ID" = $corrId } -Body $loginBody
$sw.Stop()

$customerToken = $loginRes.authData.accessToken
$customerId = $loginRes.authData.userId
if (-not $customerToken) { throw "Customer login did not return accessToken" }
Write-Host "  [PASS] Customer 'david_kim' authenticated successfully (Customer ID: $customerId)." -ForegroundColor Green
Record-Step "2. Customer Login" "POST /api/v1/auth/customers/login" 200 $sw.ElapsedMilliseconds "User: david_kim (ID: $customerId)"
$passed++

$custHeaders = @{
    "Authorization"  = "Bearer $customerToken"
    "X-Correlation-ID" = New-CorrId
}
$tellerHeaders = @{
    "Authorization"  = "Bearer $tellerToken"
    "X-Correlation-ID" = New-CorrId
}

# -----------------------------------------------------------------------------
# STEP 3: Teller Opens Secondary Account for Customer
# -----------------------------------------------------------------------------
Write-Host "`n[Step 3/8] Teller opening new savings account for customer $customerId..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$accBody = @{
    customerId  = $customerId
    accountType = "SAVINGS"
    currency    = "PHP"
} | ConvertTo-Json

$accRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/accounts" -Method Post -Headers $tellerHeaders -ContentType "application/json" -Body $accBody
$sw.Stop()

$newAccountId = $accRes.accountId
$newAccountNo = $accRes.accountNumber
if (-not $newAccountId) { throw "Account creation failed" }
Write-Host "  [PASS] Created account #$newAccountNo (Account ID: $newAccountId, Status: $($accRes.status))" -ForegroundColor Green
Record-Step "3. Open Account" "POST /api/v1/accounts" 201 $sw.ElapsedMilliseconds "Account: #$newAccountNo (ID: $newAccountId)"
$passed++

# -----------------------------------------------------------------------------
# STEP 4: Customer Submits KYC Update Request
# -----------------------------------------------------------------------------
Write-Host "`n[Step 4/8] Customer submitting KYC information update request..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$updateKycBody = @{
    newFirstName     = "David"
    newMiddleInitial = "K"
    newLastName      = "Kim-Updated"
    newAddress       = "Unit 3401 The Finance Centre, BGC, Taguig City"
    newMobileNumber  = "+639178889900"
    newCivilStatus   = "MARRIED"
    newOccupation    = "VP of Engineering"
} | ConvertTo-Json

$updateKycRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/customers/kyc/update-request" -Method Post -Headers $custHeaders -ContentType "application/json" -Body $updateKycBody
$sw.Stop()

$kycRequestId = $updateKycRes.kycRequestId
if (-not $kycRequestId) { throw "KYC update request failed to return kycRequestId" }
Write-Host "  [PASS] KYC update request #$kycRequestId submitted with status: $($updateKycRes.status)" -ForegroundColor Green
Record-Step "4. KYC Update Request" "POST /api/v1/customers/kyc/update-request" 202 $sw.ElapsedMilliseconds "KYC Request: #$kycRequestId (PENDING)"
$passed++

# -----------------------------------------------------------------------------
# STEP 5: Staff / Admin Approves KYC Update
# -----------------------------------------------------------------------------
Write-Host "`n[Step 5/8] Admin logging in and approving KYC update request #$kycRequestId..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$adminLogin = @{ username = "admin"; password = "Password123!" } | ConvertTo-Json
$adminRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/auth/staff/login" -Method Post -ContentType "application/json" -Headers @{ "X-Correlation-ID" = New-CorrId } -Body $adminLogin
$adminToken = $adminRes.authData.accessToken

$adminHeaders = @{
    "Authorization"    = "Bearer $adminToken"
    "X-Correlation-ID" = New-CorrId
}

$approveKycRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/customers/kyc/update-requests/$kycRequestId/approve" -Method Post -Headers $adminHeaders
$sw.Stop()

if ($approveKycRes.status -ne "APPROVED") { throw "Expected APPROVED status, got $($approveKycRes.status)" }
Write-Host "  [PASS] Admin approved KYC update #$kycRequestId. Status: $($approveKycRes.status)" -ForegroundColor Green
Record-Step "5. Approve KYC Update" "POST /api/v1/customers/kyc/update-requests/{id}/approve" 200 $sw.ElapsedMilliseconds "KYC Request: #$kycRequestId (APPROVED)"
$passed++

# -----------------------------------------------------------------------------
# STEP 6: Customer Submits Account Closure Request
# -----------------------------------------------------------------------------
Write-Host "`n[Step 6/8] Customer submitting closure request for account #$newAccountNo..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$closureBody = @{
    reason = "Lifecycle test: Consolidating secondary savings accounts"
} | ConvertTo-Json

$closureRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/accounts/$newAccountId/closure-request" -Method Post -Headers $custHeaders -ContentType "application/json" -Body $closureBody
$sw.Stop()

$closureRequestId = $closureRes.closureRequestId
if (-not $closureRequestId) { throw "Closure request failed to return closureRequestId" }
Write-Host "  [PASS] Closure request #$closureRequestId submitted with status: $($closureRes.status)" -ForegroundColor Green
Record-Step "6. Request Closure" "POST /api/v1/accounts/{id}/closure-request" 202 $sw.ElapsedMilliseconds "Closure Request: #$closureRequestId (PENDING)"
$passed++

# -----------------------------------------------------------------------------
# STEP 7: Admin Approves Account Closure Request
# -----------------------------------------------------------------------------
Write-Host "`n[Step 7/8] Admin approving account closure request #$closureRequestId..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$approveClosureRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/accounts/closure-requests/$closureRequestId/approve" -Method Post -Headers $adminHeaders
$sw.Stop()

if ($approveClosureRes.accountStatus -ne "CLOSED") { throw "Expected accountStatus CLOSED, got $($approveClosureRes.accountStatus)" }
Write-Host "  [PASS] Admin approved closure request #$closureRequestId. Account #$newAccountNo is CLOSED." -ForegroundColor Green
Record-Step "7. Approve Closure" "POST /api/v1/accounts/closure-requests/{id}/approve" 200 $sw.ElapsedMilliseconds "Account #$newAccountNo Status: CLOSED"
$passed++

# -----------------------------------------------------------------------------
# STEP 8: Account State & Audit Verification
# -----------------------------------------------------------------------------
Write-Host "`n[Step 8/8] Verifying finalized account status via Gateway..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$finalAccountRes = Invoke-RestMethod -Uri "$GatewayUrl/api/v1/accounts/$newAccountId" -Method Get -Headers $custHeaders
$sw.Stop()

if ($finalAccountRes.status -ne "CLOSED") { throw "Expected account status CLOSED, got $($finalAccountRes.status)" }
Write-Host "  [PASS] Confirmed Account #$newAccountNo status is CLOSED. Balance: PHP $($finalAccountRes.balance)" -ForegroundColor Green
Record-Step "8. Verify Final State" "GET /api/v1/accounts/{id}" 200 $sw.ElapsedMilliseconds "Confirmed Status: CLOSED"
$passed++

# -----------------------------------------------------------------------------
# SUMMARY REPORT TABLE
# -----------------------------------------------------------------------------
Write-Host "`n==========================================================================" -ForegroundColor Cyan
Write-Host " >>> USER LIFECYCLE FLOW EXECUTION SUMMARY REPORT" -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Cyan
$report | Format-Table -AutoSize

Write-Host "LIFECYCLE FLOW COMPLETE: ALL $passed / $total STEPS PASSED WITH ZERO ERRORS.`n" -ForegroundColor Green
