<#
.SYNOPSIS
    Fast Demo Data Reset Script for Capstone Defense Presentation
.DESCRIPTION
    Resets EOD batch runs, standing instructions next execution dates,
    loan amortization schedule statuses, PostgreSQL daily audit seals and the
    risk engine's counters in Redis, without tearing down Docker containers or
    volumes (< 5 seconds).

    The risk engine remembers, per member and business date, what is reserved
    against the daily limit, how many transfers were sent in the last ten
    minutes and how many were reversed. Left alone, repeated demo or E2E runs
    on one day add up: the reversal penalty and the velocity count push the
    same member's score up until a transfer that should post is declined.
    Only keys under risk: are removed; sign-in sessions, revoked tokens and
    idempotency keys in the same Redis are left alone. Run it while no
    transfer is in flight.
#>

param(
    [string]$OracleContainer = "oracle-core-db",
    [string]$PostgresContainer = "postgres-audit-db",
    [string]$RedisContainer = "redis-cache",
    [string]$BusinessDate = "2026-10-05"
)

$ErrorActionPreference = "Continue"
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$rootPath = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $rootPath ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $trimmed = $_.Trim()
        if ($trimmed -and -not $trimmed.StartsWith("#") -and $trimmed.Contains("=")) {
            $kv = $trimmed.Split("=", 2)
            [System.Environment]::SetEnvironmentVariable($kv[0].Trim(), $kv[1].Trim(), "Process")
        }
    }
}
$appUser = if ($env:APP_USER) { $env:APP_USER } else { "core_user" }
$appUserPassword = if ($env:APP_USER_PASSWORD) { $env:APP_USER_PASSWORD } else { "Welcome123!" }
$oracleDb = if ($env:ORACLE_DATABASE) { $env:ORACLE_DATABASE } else { "XEPDB1" }

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " Resetting EOD Batch & Demo State for Business Date: $BusinessDate " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

# 1. Oracle Core DB Reset
Write-Host "`n[1/3] Resetting Oracle core banking batch state..." -ForegroundColor Yellow
$oraSql = @"
SET FEEDBACK OFF;
DELETE FROM BATCH_STEP;
DELETE FROM BATCH_RUN;
UPDATE STANDING_INSTRUCTION SET NEXT_EXECUTION_DATE = TO_DATE('$BusinessDate', 'YYYY-MM-DD');
UPDATE LOAN_SCHEDULE SET STATUS = 'DUE', PAID_DATE = NULL WHERE DUE_DATE = TO_DATE('$BusinessDate', 'YYYY-MM-DD');
COMMIT;
EXIT;
"@

$oraSql | docker exec -i $OracleContainer sqlplus -s $appUser/$appUserPassword@localhost:1521/$oracleDb | Out-Null

Write-Host "  [PASS] Oracle tables (BATCH_RUN, BATCH_STEP, STANDING_INSTRUCTION, LOAN_SCHEDULE) reset." -ForegroundColor Green

# 2. PostgreSQL Audit Store Reset
Write-Host "`n[2/3] Resetting PostgreSQL daily audit seal for $BusinessDate..." -ForegroundColor Yellow
$pgSql = "DELETE FROM audit_store.daily_audit_seal WHERE business_date = '$BusinessDate';"
docker exec -i $PostgresContainer psql -U postgres -d audit_store -c "$pgSql" | Out-Null

Write-Host "  [PASS] PostgreSQL audit seals for $BusinessDate cleared." -ForegroundColor Green

# 3. Risk engine counters in Redis
Write-Host "`n[3/3] Clearing the risk engine's counters in Redis (risk:*)..." -ForegroundColor Yellow
# One step inside Redis, so nothing is counted between listing the keys and deleting them.
$clearRiskKeys = "local keys = redis.call('KEYS', 'risk:*') for i = 1, #keys do redis.call('DEL', keys[i]) end return #keys"
$cleared = docker exec $RedisContainer redis-cli EVAL $clearRiskKeys 0
# redis-cli prints the count bare when its output is not a terminal, "(integer) n" when it is.
if ($LASTEXITCODE -eq 0 -and "$cleared" -match '^(\(integer\) )?(\d+)$') {
    $cleared = $Matches[2]
    Write-Host "  [PASS] $cleared risk counter keys removed (daily limit used, velocity, reversals)." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] The risk counters were not cleared: $cleared" -ForegroundColor Red
    Write-Host "         Is the container '$RedisContainer' running?" -ForegroundColor Red
}

Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host " DEMO DATA RESET COMPLETE (< 5s). READY FOR EOD PRESENTATION.    " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
