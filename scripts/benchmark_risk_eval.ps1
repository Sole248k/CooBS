# ==============================================================================
# benchmark_risk_eval.ps1: Benchmark Risk Evaluation Latency SLA (FIN-805)
# Validates Handshake H5 / Endpoint R1 SLA: p95 latency < 40ms
# Target: POST /internal/v1/risk/evaluate (Port 8092)
# Reference: integrated-capstone-plans/DAY_BY_DAY_PARALLEL_EXECUTION_ROADMAP.md (Day 5)
# ==============================================================================
[CmdletBinding()]
param(
    [string]$RiskEngineUrl = "http://127.0.0.1:8092",
    [int]$Iterations = 100,
    [int]$WarmupRequests = 10,
    [double]$SlaP95ThresholdMs = 40.0,
    [string]$InternalSecret = "coobs-risk-internal-secret-2026",
    [string]$JwtSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
)

$ErrorActionPreference = "Stop"

# Auto-normalize localhost to 127.0.0.1 on Windows to bypass IPv6 (::1) Docker loopback SYN timeout (~45ms)
if ($RiskEngineUrl -match "localhost") {
    $RiskEngineUrl = $RiskEngineUrl -replace "localhost", "127.0.0.1"
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " CooBS Platform - Risk Evaluation SLA Latency Benchmark (FIN-805) " -ForegroundColor Cyan
Write-Host " Target SLA: p95 < $SlaP95ThresholdMs ms (Endpoint: POST /internal/v1/risk/evaluate)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# ------------------------------------------------------------------------------
# 1. Initialize Persistent Connection Pool
# ------------------------------------------------------------------------------
Add-Type -AssemblyName System.Net.Http
$httpClient = New-Object System.Net.Http.HttpClient
$httpClient.Timeout = [TimeSpan]::FromSeconds(5)

# ------------------------------------------------------------------------------
# 2. Healthcheck & Target Connectivity Probe
# ------------------------------------------------------------------------------
Write-Host "`n[1/5] Probing risk-engine health ($RiskEngineUrl/actuator/health)..." -ForegroundColor Cyan
try {
    $healthResp = $httpClient.GetAsync("$RiskEngineUrl/actuator/health").GetAwaiter().GetResult()
    if ($healthResp.IsSuccessStatusCode) {
        Write-Host "  [PASS] risk-engine is UP and healthy (HTTP $([int]$healthResp.StatusCode))" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] Unexpected health status code: $([int]$healthResp.StatusCode)" -ForegroundColor Yellow
    }
    $healthResp.Dispose()
} catch {
    Write-Error "Failed to connect to risk-engine at $RiskEngineUrl. Ensure container is running on port 8092."
    $httpClient.Dispose()
    exit 1
}

# ------------------------------------------------------------------------------
# 3. Authentication Setup (Service JWT / Internal Secret Handshake)
# ------------------------------------------------------------------------------
Write-Host "`n[2/5] Establishing internal service credentials..." -ForegroundColor Cyan

function New-ServiceJwtToken([string]$Secret) {
    try {
        $keyBytes = [System.Convert]::FromBase64String($Secret)
    } catch {
        $keyBytes = [System.Text.Encoding]::UTF8.GetBytes($Secret)
    }
    if ($keyBytes.Length -lt 32) {
        $padded = New-Object byte[] 32
        [System.Array]::Copy($keyBytes, $padded, [Math]::Min($keyBytes.Length, 32))
        $keyBytes = $padded
    }
    $headerJson = '{"alg":"HS256","typ":"JWT"}'
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $exp = $now + 86400
    $payloadJson = '{"sub":"service-internal","userId":0,"roles":["ROLE_SERVICE"],"iat":' + $now + ',"exp":' + $exp + '}'

    function Base64UrlEncode([byte[]]$bytes) {
        return [System.Convert]::ToBase64String($bytes).Replace('+', '-').Replace('/', '_').TrimEnd('=')
    }

    $hB64 = Base64UrlEncode ([System.Text.Encoding]::UTF8.GetBytes($headerJson))
    $pB64 = Base64UrlEncode ([System.Text.Encoding]::UTF8.GetBytes($payloadJson))
    $toSign = [System.Text.Encoding]::UTF8.GetBytes("$hB64.$pB64")

    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = $keyBytes
    $sigB64 = Base64UrlEncode ($hmac.ComputeHash($toSign))

    return "$hB64.$pB64.$sigB64"
}

$ServiceJwt = New-ServiceJwtToken $JwtSecret
$httpClient.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $ServiceJwt)
if (-not [string]::IsNullOrEmpty($InternalSecret)) {
    $httpClient.DefaultRequestHeaders.Add("X-Internal-Secret", $InternalSecret)
}

# Verify authentication handshake with probe request
$probeJson = '{"transactionId":"' + [guid]::NewGuid().ToString() + '","customerId":12,"amount":100.00,"currency":"PHP"}'
$pContent = New-Object System.Net.Http.StringContent($probeJson, [System.Text.Encoding]::UTF8, "application/json")
$probeResp = $httpClient.PostAsync("$RiskEngineUrl/internal/v1/risk/evaluate", $pContent).GetAwaiter().GetResult()

if ($probeResp.IsSuccessStatusCode) {
    Write-Host "  [PASS] Authenticated successfully with internal service credentials (ROLE_SERVICE)" -ForegroundColor Green
} else {
    Write-Error "Authentication handshake failed with HTTP status: $([int]$probeResp.StatusCode)"
    $probeResp.Dispose()
    $pContent.Dispose()
    $httpClient.Dispose()
    exit 1
}
$probeResp.Dispose()
$pContent.Dispose()

# ------------------------------------------------------------------------------
# 4. Warm-up Phase (JIT compilation, Redis connection pool warm-up)
# ------------------------------------------------------------------------------
Write-Host "`n[3/5] Warming up JVM JIT compiler and Redis connection pool ($WarmupRequests requests)..." -ForegroundColor Cyan
for ($w = 1; $w -le $WarmupRequests; $w++) {
    $wCust = 2000 + ($w % 5)
    $wJson = '{"transactionId":"' + [guid]::NewGuid().ToString() + '","customerId":' + $wCust + ',"sourceAccountId":"ACC_10000012","destinationAccountId":"ACC_10000016","amount":150.00,"currency":"PHP","channel":"APP","kycTier":"TIER_2_STANDARD","dailyLimit":50000.00,"isNewBeneficiary":false}'
    $wContent = New-Object System.Net.Http.StringContent($wJson, [System.Text.Encoding]::UTF8, "application/json")
    $wResp = $httpClient.PostAsync("$RiskEngineUrl/internal/v1/risk/evaluate", $wContent).GetAwaiter().GetResult()
    $wResp.Dispose()
    $wContent.Dispose()
}
Write-Host "  [PASS] Warm-up complete. Connection pool & execution paths primed." -ForegroundColor Green

# ------------------------------------------------------------------------------
# 5. Benchmark Execution Phase
# ------------------------------------------------------------------------------
Write-Host "`n[4/5] Executing latency benchmark ($Iterations iterations)..." -ForegroundColor Cyan

$ClientLatencies = New-Object System.Collections.Generic.List[double]
$ServerLatencies = New-Object System.Collections.Generic.List[double]
$Decisions = @{ "APPROVE" = 0; "DECLINE" = 0 }
$FailedRequests = 0

$Sw = New-Object System.Diagnostics.Stopwatch

for ($i = 1; $i -le $Iterations; $i++) {
    $custId = 3000 + ($i % 25)
    $isNew = ($i % 10 -eq 0)
    $amount = if ($i % 20 -eq 0) { 75000.00 } else { [math]::Round((Get-Random -Minimum 50 -Maximum 1000), 2) }

    $bJson = '{"transactionId":"' + [guid]::NewGuid().ToString() + '","customerId":' + $custId + ',"sourceAccountId":"ACC_' + $custId + '","destinationAccountId":"ACC_99999999","amount":' + $amount + ',"currency":"PHP","channel":"APP","kycTier":"TIER_2_STANDARD","dailyLimit":50000.00,"isNewBeneficiary":' + ($isNew.ToString().ToLower()) + '}'
    $bContent = New-Object System.Net.Http.StringContent($bJson, [System.Text.Encoding]::UTF8, "application/json")

    $Sw.Restart()
    try {
        $resp = $httpClient.PostAsync("$RiskEngineUrl/internal/v1/risk/evaluate", $bContent).GetAwaiter().GetResult()
        $Sw.Stop()

        $elapsedMs = [math]::Round($Sw.Elapsed.TotalMilliseconds, 2)

        if ($resp.IsSuccessStatusCode) {
            $ClientLatencies.Add($elapsedMs)
            $raw = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $resObj = $raw | ConvertFrom-Json

            if ($resObj.evaluatedInMs) {
                $ServerLatencies.Add([double]$resObj.evaluatedInMs)
            }

            $dec = if ($resObj.decision) { $resObj.decision } else { "UNKNOWN" }
            if ($Decisions.ContainsKey($dec)) {
                $Decisions[$dec]++
            } else {
                $Decisions[$dec] = 1
            }
        } else {
            $FailedRequests++
            Write-Host "  [FAIL] Request $i returned status: $([int]$resp.StatusCode)" -ForegroundColor Yellow
        }
        $resp.Dispose()
    } catch {
        $Sw.Stop()
        $FailedRequests++
        Write-Host "  [FAIL] Request $i encountered an error: $_" -ForegroundColor Yellow
    } finally {
        $bContent.Dispose()
    }

    if ($i % 25 -eq 0 -or $i -eq $Iterations) {
        Write-Host "  Progress: $i / $Iterations completed..." -ForegroundColor Gray
    }
}

# Clean up client
$httpClient.Dispose()

# ------------------------------------------------------------------------------
# 6. Statistical Distribution Calculation & SLA Evaluation
# ------------------------------------------------------------------------------
Write-Host "`n[5/5] Computing latency percentiles & SLA compliance..." -ForegroundColor Cyan

function Get-Percentile([System.Collections.Generic.List[double]]$list, [double]$percentile) {
    if ($list.Count -eq 0) { return 0.0 }
    $sorted = $list | Sort-Object
    $idx = [math]::Ceiling(($percentile / 100.0) * $sorted.Count) - 1
    if ($idx -lt 0) { $idx = 0 }
    if ($idx -ge $sorted.Count) { $idx = $sorted.Count - 1 }
    return [math]::Round($sorted[$idx], 2)
}

$cMin = ($ClientLatencies | Measure-Object -Minimum).Minimum
$cMax = ($ClientLatencies | Measure-Object -Maximum).Maximum
$cAvg = [math]::Round(($ClientLatencies | Measure-Object -Average).Average, 2)
$cP50 = Get-Percentile $ClientLatencies 50
$cP90 = Get-Percentile $ClientLatencies 90
$cP95 = Get-Percentile $ClientLatencies 95
$cP99 = Get-Percentile $ClientLatencies 99

$sMin = ($ServerLatencies | Measure-Object -Minimum).Minimum
$sMax = ($ServerLatencies | Measure-Object -Maximum).Maximum
$sAvg = [math]::Round(($ServerLatencies | Measure-Object -Average).Average, 2)
$sP50 = Get-Percentile $ServerLatencies 50
$sP90 = Get-Percentile $ServerLatencies 90
$sP95 = Get-Percentile $ServerLatencies 95
$sP99 = Get-Percentile $ServerLatencies 99

Write-Host "`n=================================================================" -ForegroundColor DarkGray
Write-Host "            BENCHMARK RESULTS SUMMARY (Sample Size: $Iterations)          " -ForegroundColor White
Write-Host "=================================================================" -ForegroundColor DarkGray
Write-Host ("{0,-18} | {1,-18} | {2,-18}" -f "Metric", "Client Round-Trip", "Server Evaluator")
Write-Host "-------------------+--------------------+-------------------"
Write-Host ("{0,-18} | {1,-18} | {2,-18}" -f "Minimum (p0)", "$cMin ms", "$sMin ms")
Write-Host ("{0,-18} | {1,-18} | {2,-18}" -f "Median (p50)", "$cP50 ms", "$sP50 ms")
Write-Host ("{0,-18} | {1,-18} | {2,-18}" -f "Average (mean)", "$cAvg ms", "$sAvg ms")
Write-Host ("{0,-18} | {1,-18} | {2,-18}" -f "90th %-tile (p90)", "$cP90 ms", "$sP90 ms")
Write-Host ("{0,-18} | {1,-18} | {2,-18}" -f "95th %-tile (p95)", "$cP95 ms", "$sP95 ms") -ForegroundColor Yellow
Write-Host ("{0,-18} | {1,-18} | {2,-18}" -f "99th %-tile (p99)", "$cP99 ms", "$sP99 ms")
Write-Host ("{0,-18} | {1,-18} | {2,-18}" -f "Maximum (p100)", "$cMax ms", "$sMax ms")
Write-Host "-------------------+--------------------+-------------------"
Write-Host "Decisions Recorded : APPROVE: $($Decisions['APPROVE']), DECLINE: $($Decisions['DECLINE'])"
Write-Host "Failed Requests    : $FailedRequests (Error Rate: $([math]::Round(($FailedRequests / $Iterations) * 100, 2))%)"
Write-Host "=================================================================" -ForegroundColor DarkGray

Write-Host "`nSLA VERIFICATION:" -ForegroundColor White
Write-Host "  Target SLA Threshold : p95 < $SlaP95ThresholdMs ms" -ForegroundColor Cyan
Write-Host "  Observed Client p95  : $cP95 ms" -ForegroundColor $(if ($cP95 -le $SlaP95ThresholdMs) { "Green" } else { "Red" })
Write-Host "  Observed Server p95  : $sP95 ms" -ForegroundColor $(if ($sP95 -le $SlaP95ThresholdMs) { "Green" } else { "Red" })

if ($cP95 -le $SlaP95ThresholdMs -and $FailedRequests -eq 0) {
    Write-Host "`n=================================================================" -ForegroundColor Green
    Write-Host " [SUCCESS] Risk Evaluation SLA Benchmark Verified (FIN-805)       " -ForegroundColor Green
    Write-Host "           Observed p95 ($cP95 ms) comfortably met < $SlaP95ThresholdMs ms SLA! " -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n=================================================================" -ForegroundColor Red
    Write-Host " [FAILURE] Risk Evaluation SLA Threshold Breached or Errors Detected " -ForegroundColor Red
    Write-Host "=================================================================" -ForegroundColor Red
    exit 1
}
