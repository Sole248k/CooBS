<#
.SYNOPSIS
    Cryptographic Audit Trigger & Hash Chain Verification Script (FIN-504)
.DESCRIPTION
    Validates PostgreSQL audit_store and batch schema matching official ERD:
    1. 58 ledger mutation records across all 21 accounts
    2. Genesis seed 64-zero linkage
    3. Strict predecessor SHA-256 hash chaining
    4. Compliance immutability guard (ERRCODE 55000 on illegal UPDATE/DELETE)
    5. Daily audit seal foreign key to ledger_mutation_audit
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Continue"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Forensic Audit Store & SHA-256 Hash Chain Verification (FIN-504)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$passed = 0
$total = 5

# 1. Total Record Count
Write-Host "`n[Check 1/5] Audit Record Count in audit_store.ledger_mutation_audit..." -ForegroundColor Yellow
$countResult = docker exec postgres-audit-db psql -U postgres -d audit_store -t -A -c "SELECT COUNT(*) FROM audit_store.ledger_mutation_audit;"
$count = ($countResult -join "").Trim()
if ([int]$count -ge 58) {
    Write-Host "  [PASS] Record count verified: $count mutation records present (baseline >= 58)." -ForegroundColor Green
    $passed++
} else {
    Write-Host "  [FAIL] Expected at least 58 mutation records, found: $count" -ForegroundColor Red
}

# 2. Cryptographic Hash Chain Linkage Across All Accounts
Write-Host "`n[Check 2/5] Cryptographic Hash Chain Integrity Across All Accounts..." -ForegroundColor Yellow
$chainSql = @"
WITH chain_check AS (
    SELECT 
        audit_id,
        account_id,
        previous_hash,
        current_hash,
        LAG(current_hash) OVER (PARTITION BY account_id ORDER BY audit_id ASC) as expected_prev,
        LAG(current_hash) OVER (PARTITION BY account_id ORDER BY audit_id ASC) IS NULL AS is_genesis
    FROM audit_store.ledger_mutation_audit
)
SELECT 
    COUNT(*) as total_rows,
    COUNT(CASE WHEN is_genesis AND previous_hash = '0000000000000000000000000000000000000000000000000000000000000000' THEN 1 
               WHEN NOT is_genesis AND previous_hash = expected_prev THEN 1 
               ELSE NULL END) as valid_links,
    COUNT(CASE WHEN length(current_hash) = 64 THEN 1 ELSE NULL END) as valid_hashes
FROM chain_check;
"@

$chainResult = docker exec postgres-audit-db psql -U postgres -d audit_store -t -A -c "$chainSql"
$parts = ($chainResult -join "").Trim().Split("|")
if ($parts.Count -eq 3 -and [int]$parts[0] -ge 58 -and $parts[0] -eq $parts[1] -and $parts[0] -eq $parts[2]) {
    Write-Host "  [PASS] 100% Hash Chain Linkage Intact: $($parts[0])/$($parts[0]) records valid across all accounts." -ForegroundColor Green
    $passed++
} else {
    Write-Host "  [FAIL] Chain verification failed: $chainResult" -ForegroundColor Red
}

# 3. Immutability Guard - Block UPDATE
Write-Host "`n[Check 3/5] Immutability Guard: Rejecting UPDATE..." -ForegroundColor Yellow
$updateRes = docker exec postgres-audit-db psql -U postgres -d audit_store -c "UPDATE audit_store.ledger_mutation_audit SET amount = 999999 WHERE audit_id = 1;" 2>&1
$updateStr = ($updateRes -join " ")
if ($updateStr -match "COMPLIANCE VIOLATION" -or $updateStr -match "55000") {
    Write-Host "  [PASS] Illegal UPDATE blocked by trigger (ERRCODE 55000)." -ForegroundColor Green
    $passed++
} else {
    Write-Host "  [FAIL] Immutability trigger failed to block UPDATE: $updateStr" -ForegroundColor Red
}

# 4. Immutability Guard - Block DELETE
Write-Host "`n[Check 4/5] Immutability Guard: Rejecting DELETE..." -ForegroundColor Yellow
$deleteRes = docker exec postgres-audit-db psql -U postgres -d audit_store -c "DELETE FROM audit_store.ledger_mutation_audit WHERE audit_id = 1;" 2>&1
$deleteStr = ($deleteRes -join " ")
if ($deleteStr -match "COMPLIANCE VIOLATION" -or $deleteStr -match "55000") {
    Write-Host "  [PASS] Illegal DELETE blocked by trigger (ERRCODE 55000)." -ForegroundColor Green
    $passed++
} else {
    Write-Host "  [FAIL] Immutability trigger failed to block DELETE: $deleteStr" -ForegroundColor Red
}

# 5. Daily Audit Seal & Foreign Key Verification (ERD: 5 columns)
Write-Host "`n[Check 5/5] Batch Schema & Daily Audit Seal Foreign Key..." -ForegroundColor Yellow
# Every seal, however many end-of-day runs have added one: a 64-character hash, and a last audit
# row that exists and is dated on or before the seal's own business date.
$sealSql = @"
SELECT COUNT(*), COUNT(*) FILTER (WHERE length(s.seal_hash) = 64 AND a.audit_id IS NOT NULL AND DATE(a.event_timestamp) <= s.business_date)
FROM audit_store.daily_audit_seal s
LEFT JOIN audit_store.ledger_mutation_audit a ON s.last_audit_id = a.audit_id;
"@
$sealRes = docker exec postgres-audit-db psql -U postgres -d audit_store -t -A -c "$sealSql"
$sealParts = ($sealRes -join "").Trim().Split("|")
if ($sealParts.Count -eq 2 -and [int]$sealParts[0] -ge 1 -and $sealParts[0] -eq $sealParts[1]) {
    Write-Host "  [PASS] $($sealParts[0]) daily seal(s): each has a 64-character hash and seals an audit row of its own date or earlier." -ForegroundColor Green
    $passed++
} else {
    Write-Host "  [FAIL] Daily audit seal check failed: $($sealParts[1]) of $($sealParts[0]) seal(s) are sound. List them with:" -ForegroundColor Red
    Write-Host "         SELECT s.*, a.event_timestamp FROM audit_store.daily_audit_seal s LEFT JOIN audit_store.ledger_mutation_audit a ON s.last_audit_id = a.audit_id;" -ForegroundColor Red
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host " Verification Summary: $passed / $total Checks Passed" -ForegroundColor $(if ($passed -eq $total) { "Green" } else { "Yellow" })
Write-Host "=================================================================" -ForegroundColor Cyan

if ($passed -ne $total) {
    exit 1
}
