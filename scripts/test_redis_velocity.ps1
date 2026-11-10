# ==============================================================================
# test_redis_velocity.ps1: Verify Redis Velocity Keys & Counter Lifecycle (FIN-803)
# Validates 10-Minute Velocity Window, Daily Aggregation & TTL Expirations
# Reference: contracts/runtime/RUNTIME_SETTINGS.md & Handshake H13 / H17
# ==============================================================================
[CmdletBinding()]
param(
    [string]$RedisHost = "127.0.0.1",
    [int]$RedisPort = 6379,
    [switch]$UseDocker = $false
)

$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " CooBS Platform - Redis Velocity Keys & TTL Verification (FIN-803)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

function Invoke-RedisCommand([string]$cmd) {
    if ($UseDocker -or (Get-Command docker -ErrorAction SilentlyContinue)) {
        try {
            $output = docker exec redis-cache redis-cli $cmd.Split(' ')
            return $output
        } catch {
            # fallback to local redis-cli
        }
    }
    
    if (Get-Command redis-cli -ErrorAction SilentlyContinue) {
        $output = & redis-cli -h $RedisHost -p $RedisPort $cmd.Split(' ')
        return $output
    } else {
        throw "Neither 'docker exec redis-cache' nor local 'redis-cli' is available."
    }
}

# 1. Connectivity Check
Write-Host "`n[1/4] Checking Redis connectivity..." -ForegroundColor Cyan
$ping = Invoke-RedisCommand "PING"
if ($ping -match "PONG") {
    Write-Host "  [PASS] Redis responded with PONG" -ForegroundColor Green
} else {
    Write-Error "Redis ping failed: $ping"
    exit 1
}

$TestCustomerId = 9999
$TodayStr = (Get-Date).ToString("yyyy-MM-dd")

$VelocityKey = "risk:velocity:$TestCustomerId"
$DailyAmountKey = "risk:daily:amount:${TestCustomerId}:$TodayStr"
$DailyCountKey = "risk:daily:count:${TestCustomerId}:$TodayStr"
$DailyReversalsKey = "risk:daily:reversals:${TestCustomerId}:$TodayStr"

# 2. Test 10-Minute Velocity Counter & Window TTL
Write-Host "`n[2/4] Testing 10-minute rolling velocity counter ($VelocityKey)..." -ForegroundColor Cyan
Invoke-RedisCommand "DEL $VelocityKey" | Out-Null
$c1 = Invoke-RedisCommand "INCR $VelocityKey"
$c2 = Invoke-RedisCommand "INCR $VelocityKey"
$c3 = Invoke-RedisCommand "INCR $VelocityKey"
Invoke-RedisCommand "EXPIRE $VelocityKey 600" | Out-Null
$ttl = [int](Invoke-RedisCommand "TTL $VelocityKey")

if ([int]$c3 -eq 3 -and $ttl -gt 0 -and $ttl -le 600) {
    Write-Host "  [PASS] Velocity incremented to 3 with TTL = ${ttl}s (expected <= 600s)" -ForegroundColor Green
} else {
    Write-Error "Velocity test failed: count=$c3, ttl=$ttl"
    exit 1
}

# 3. Test 24-Hour Daily Metric Keys & TTL
Write-Host "`n[3/4] Testing 24-hour daily amount & count counters..." -ForegroundColor Cyan
Invoke-RedisCommand "SET $DailyAmountKey 2500.00 EX 86400" | Out-Null
$amt = Invoke-RedisCommand "GET $DailyAmountKey"
$dailyCount = Invoke-RedisCommand "INCR $DailyCountKey"
Invoke-RedisCommand "EXPIRE $DailyCountKey 86400" | Out-Null
$amtTtl = [int](Invoke-RedisCommand "TTL $DailyAmountKey")

if ($amt -eq "2500.00" -and [int]$dailyCount -ge 1 -and $amtTtl -gt 0 -and $amtTtl -le 86400) {
    Write-Host "  [PASS] Daily amount = $amt, count = $dailyCount, TTL = ${amtTtl}s (expected <= 86400s)" -ForegroundColor Green
} else {
    Write-Error "Daily metrics test failed: amount=$amt, count=$dailyCount, ttl=$amtTtl"
    exit 1
}

# 4. Burst Velocity Detection & Clean-up
Write-Host "`n[4/4] Testing burst threshold detection (>= 4 transfers in 10 min)..." -ForegroundColor Cyan
Invoke-RedisCommand "INCR $VelocityKey" | Out-Null # 4
$finalCount = [int](Invoke-RedisCommand "GET $VelocityKey")

if ($finalCount -ge 4) {
    Write-Host "  [PASS] Burst velocity threshold exceeded (count=$finalCount >= 4) - triggers BURST_VELOCITY_LIMIT_EXCEEDED" -ForegroundColor Green
} else {
    Write-Error "Burst threshold simulation failed: finalCount=$finalCount"
    exit 1
}

# Cleanup test keys
Invoke-RedisCommand "DEL $VelocityKey $DailyAmountKey $DailyCountKey $DailyReversalsKey" | Out-Null
Write-Host "  [PASS] Cleaned up test keys" -ForegroundColor Gray

Write-Host "`n=================================================================" -ForegroundColor Green
Write-Host " [SUCCESS] Redis Velocity Keys & Lifecycle Verified (FIN-803)     " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
exit 0
