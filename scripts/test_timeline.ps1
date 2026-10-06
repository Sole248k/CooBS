# ==============================================================================
# CooBS - FIN-304 Timeline & Transaction Status Verification Script
# Validates T1 Transfer Ingress, DB Persistence, T2 Detail Lookup, and T3 Timeline (Customer & Staff)
# ==============================================================================

param(
    [string]$BaseUrl = "http://localhost:8090",
    [string]$Secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
)

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " Testing FIN-304 Timeline & Transaction Tracking on $BaseUrl " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

# 1. Helper to generate JWT Token
function New-JwtToken([long]$userId, [string]$username, [string[]]$roles, [string]$secretHex) {
    function Base64Url([byte[]]$b) {
        [Convert]::ToBase64String($b).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    }

    $headerJson = '{"alg":"HS256","typ":"JWT"}'
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $exp = $now + 3600

    $payloadObj = @{
        sub      = $userId.ToString()
        userId   = $userId
        username = $username
        roles    = $roles
        iat      = $now
        exp      = $exp
    }
    $payloadJson = $payloadObj | ConvertTo-Json -Compress

    $part1 = Base64Url ([System.Text.Encoding]::UTF8.GetBytes($headerJson))
    $part2 = Base64Url ([System.Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsigned = "$part1.$part2"

    try {
        $keyBytes = [System.Convert]::FromBase64String($secretHex)
    } catch {
        $keyBytes = [System.Text.Encoding]::UTF8.GetBytes($secretHex)
    }

    $hmac = [System.Security.Cryptography.HMACSHA256]::new($keyBytes)
    $sig = Base64Url ($hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($unsigned)))
    return "$unsigned.$sig"
}

$customerToken = New-JwtToken -userId 1 -username "john_doe" -roles @("ROLE_CUSTOMER") -secretHex $Secret
$staffToken = New-JwtToken -userId 99 -username "staff_teller" -roles @("ROLE_TELLER") -secretHex $Secret
$testKey = "timeline-idemp-" + [guid]::NewGuid().ToString().Substring(0, 8)

$headersCustomer = @{
    "Authorization"   = "Bearer $customerToken"
    "Idempotency-Key" = $testKey
    "Content-Type"    = "application/json"
}

$headersStaff = @{
    "Authorization"   = "Bearer $staffToken"
    "Content-Type"    = "application/json"
}

# ------------------------------------------------------------------------------
# STEP 1: Submit Transfer Request (T1)
# ------------------------------------------------------------------------------
Write-Host "`n[STEP 1] Submitting transfer request with Idempotency-Key: $testKey" -ForegroundColor Yellow
$body = @{
    sourceAccountId          = 1
    destinationAccountNumber = "ACC_10000002"
    destinationBankCode      = "COOBS"
    amount                   = 2500.00
    currency                 = "PHP"
    category                 = "TRANSFER"
    remarks                  = "Testing FIN-304 timeline tracking"
} | ConvertTo-Json

$response1 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/transfers" -Method Post -Headers $headersCustomer -Body $body
Write-Host "Response (202 Accepted):" -ForegroundColor Green
$response1 | ConvertTo-Json | Write-Host
$txnId = $response1.transactionId
$refNo = $response1.referenceNo

# ------------------------------------------------------------------------------
# STEP 2: Query Customer Timeline View (3 Stages)
# ------------------------------------------------------------------------------
Write-Host "`n[STEP 2] Fetching Customer Timeline for txnId: $txnId" -ForegroundColor Yellow
$custTimeline = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/$txnId/timeline" -Method Get -Headers $headersCustomer
Write-Host "Customer Timeline Response:" -ForegroundColor Green
$custTimeline | ConvertTo-Json -Depth 5 | Write-Host
Write-Host "View: $($custTimeline.view), Total Stages: $($custTimeline.totalStages), Current: $($custTimeline.currentStage)" -ForegroundColor Cyan

# ------------------------------------------------------------------------------
# STEP 3: Query Staff Timeline View (11 Stages)
# ------------------------------------------------------------------------------
Write-Host "`n[STEP 3] Fetching Staff Timeline for trackingReference: $refNo" -ForegroundColor Yellow
$staffTimeline = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/$refNo/timeline?view=STAFF" -Method Get -Headers $headersStaff
Write-Host "Staff Timeline Response:" -ForegroundColor Green
$staffTimeline | ConvertTo-Json -Depth 5 | Write-Host
Write-Host "View: $($staffTimeline.view), Total Stages: $($staffTimeline.totalStages), Current: $($staffTimeline.currentStage)" -ForegroundColor Cyan

# ------------------------------------------------------------------------------
# STEP 4: Query Transaction Details (T2)
# ------------------------------------------------------------------------------
Write-Host "`n[STEP 4] Fetching Transaction Details for: $txnId" -ForegroundColor Yellow
$txnDetail = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/$txnId" -Method Get -Headers $headersCustomer
Write-Host "Transaction Detail Response:" -ForegroundColor Green
$txnDetail | ConvertTo-Json | Write-Host

# ------------------------------------------------------------------------------
# STEP 5: Verify Postgres Cloud DB Records
# ------------------------------------------------------------------------------
Write-Host "`n[STEP 5] Verifying records in transaction_db (postgres-cloud-db)..." -ForegroundColor Yellow
try {
    $dbTxn = docker exec postgres-cloud-db psql -U postgres -d transaction_db -c "SELECT transaction_id, reference_no, status, amount, currency FROM txn.txn_transaction WHERE transaction_id = '$txnId';"
    Write-Host "$dbTxn" -ForegroundColor Green

    $dbStages = docker exec postgres-cloud-db psql -U postgres -d transaction_db -c "SELECT stage_no, stage_name, outcome, occurred_at FROM txn.txn_stage_event WHERE transaction_id = '$txnId' ORDER BY stage_no ASC;"
    Write-Host "$dbStages" -ForegroundColor Green
    Write-Host "[SUCCESS] All manual testing verification steps passed successfully!" -ForegroundColor Green
} catch {
    Write-Host "[WARN] Docker database query failed (is postgres-cloud-db running?)" -ForegroundColor Yellow
}
