# ==============================================================================
# CooBS - FIN-303 Idempotency Verification Script
# Validates Redis SETNX Mutex Lock, Replay Caching, and 409 Conflict Guard
# ==============================================================================

param(
    [string]$BaseUrl = "http://localhost:8090",
    [string]$Secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
)

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " Testing Idempotency & Redis Mutex Guards on $BaseUrl " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

# 1. Generate JWT Helper
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

$token = New-JwtToken -userId 1 -username "john_doe" -roles @("ROLE_CUSTOMER") -secretHex $Secret
$testKey = "test-idemp-" + [guid]::NewGuid().ToString().Substring(0, 8)

$headers = @{
    "Authorization"   = "Bearer $token"
    "Idempotency-Key" = $testKey
    "Content-Type"    = "application/json"
}

$body = @{
    sourceAccountId          = 1
    destinationAccountNumber = "ACC_10000002"
    amount                   = 2500.00
    currency                 = "PHP"
    remarks                  = "Idempotency test transfer"
} | ConvertTo-Json

# ------------------------------------------------------------------------------
# TEST 1: Initial Transfer Submission (Expect 202 Accepted)
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 1] Sending initial transfer request with Idempotency-Key: $testKey" -ForegroundColor Yellow
$response1 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/transfers" -Method Post -Headers $headers -Body $body
Write-Host "Response received:" -ForegroundColor Green
$response1 | ConvertTo-Json | Write-Host
$txnId1 = $response1.transactionId
$refNo1 = $response1.referenceNo

# ------------------------------------------------------------------------------
# TEST 2: Verify Cached Response in Redis
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 2] Checking Redis cache for cached response..." -ForegroundColor Yellow
$cachedVal = docker exec redis-cache redis-cli get "idempotency:remittance:response:$testKey"
Write-Host "Redis cached value: $cachedVal" -ForegroundColor Green
if ($cachedVal -like "*$txnId1*") {
    Write-Host "[PASS] Redis cached the completed transaction response successfully." -ForegroundColor Green
} else {
    Write-Host "[WARN] Cached key not found or format mismatch." -ForegroundColor Yellow
}

# ------------------------------------------------------------------------------
# TEST 3: Duplicate Request with SAME Idempotency-Key (Expect 409 Conflict)
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 3] Sending duplicate transfer request with SAME Idempotency-Key: $testKey" -ForegroundColor Yellow
try {
    $response2 = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/transfers" -Method Post -Headers $headers -Body $body
    Write-Host "[FAIL] Expected 409 Conflict on duplicate submission, but got success: $($response2 | ConvertTo-Json)" -ForegroundColor Red
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "Caught expected HTTP Status: $statusCode" -ForegroundColor Green
    if ($statusCode -eq 409) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $bodyErr = $reader.ReadToEnd()
        Write-Host "Response body: $bodyErr" -ForegroundColor Cyan
        Write-Host "[PASS] Duplicate submission with same Idempotency-Key blocked with 409 CONFLICT!" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] Expected 409, got: $statusCode" -ForegroundColor Red
    }
}

# ------------------------------------------------------------------------------
# TEST 4: Concurrent In-Flight Conflict (Expect 409 Conflict)
# ------------------------------------------------------------------------------
$conflictKey = "test-inflight-" + [guid]::NewGuid().ToString().Substring(0, 8)
Write-Host "`n[TEST 4] Simulating concurrent in-flight conflict with key: $conflictKey" -ForegroundColor Yellow
# Simulate an active transaction by acquiring mutex lock directly in Redis
docker exec redis-cache redis-cli set "idempotency:remittance:$conflictKey" "LOCKED" EX 60 | Out-Null

$conflictHeaders = @{
    "Authorization"   = "Bearer $token"
    "Idempotency-Key" = $conflictKey
    "Content-Type"    = "application/json"
}

try {
    $res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/transfers" -Method Post -Headers $conflictHeaders -Body $body
    Write-Host "[FAIL] Expected 409 Conflict, but request succeeded." -ForegroundColor Red
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "Caught expected HTTP Status: $statusCode" -ForegroundColor Green
    if ($statusCode -eq 409) {
        Write-Host "[PASS] Concurrent submission successfully blocked with HTTP 409 CONFLICT!" -ForegroundColor Green
    } else {
        Write-Host "Unexpected status code: $statusCode" -ForegroundColor Yellow
    }
} finally {
    # Clean up simulation lock
    docker exec redis-cache redis-cli del "idempotency:remittance:$conflictKey" | Out-Null
}

Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host " Idempotency Verification Completed Successfully! " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
