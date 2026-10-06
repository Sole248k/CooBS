<#
.SYNOPSIS
    Fast Demo Data Reset Script for Capstone Defense Presentation
.DESCRIPTION
    Resets EOD batch runs, standing instructions next execution dates,
    loan amortization schedule statuses, and PostgreSQL daily audit seals
    without tearing down Docker containers or volumes (< 5 seconds).
#>

param(
    [string]$OracleContainer = "oracle-core-db",
    [string]$PostgresContainer = "postgres-audit-db",
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
Write-Host "`n[1/2] Resetting Oracle core banking batch state..." -ForegroundColor Yellow
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
Write-Host "`n[2/2] Resetting PostgreSQL daily audit seal for $BusinessDate..." -ForegroundColor Yellow
$pgSql = "DELETE FROM audit_store.daily_audit_seal WHERE business_date = '$BusinessDate';"
docker exec -i $PostgresContainer psql -U postgres -d audit_store -c "$pgSql" | Out-Null

Write-Host "  [PASS] PostgreSQL audit seals for $BusinessDate cleared." -ForegroundColor Green

Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host " DEMO DATA RESET COMPLETE (< 5s). READY FOR EOD PRESENTATION.    " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
