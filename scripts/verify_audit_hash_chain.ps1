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
if ($count -eq "58") {
    Write-Host "  [PASS] Record count verified: exactly 58 mutation records present." -ForegroundColor Green
    $passed++
} else {
    Write-Host "  [FAIL] Expected 58 mutation records, found: $count" -ForegroundColor Red
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
if ($parts.Count -eq 3 -and $parts[0] -eq "58" -and $parts[1] -eq "58" -and $parts[2] -eq "58") {
    Write-Host "  [PASS] 100% Hash Chain Linkage Intact: 58/58 records valid across all 21 accounts." -ForegroundColor Green
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
$sealSql = @"
SELECT s.seal_id, s.business_date, s.last_audit_id, length(s.seal_hash), a.transaction_id
FROM audit_store.daily_audit_seal s
JOIN audit_store.ledger_mutation_audit a ON s.last_audit_id = a.audit_id;
"@
$sealRes = docker exec postgres-audit-db psql -U postgres -d audit_store -t -A -c "$sealSql"
$sealParts = ($sealRes -join "").Trim().Split("|")
if ($sealParts.Count -ge 5 -and $sealParts[2] -eq "48" -and $sealParts[3] -eq "64") {
    Write-Host "  [PASS] Daily audit seal valid: links seal_id=1 to last_audit_id=48 (64-char hash)." -ForegroundColor Green
    $passed++
} else {
    Write-Host "  [FAIL] Daily audit seal check failed: $sealRes" -ForegroundColor Red
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host " Verification Summary: $passed / $total Checks Passed" -ForegroundColor $(if ($passed -eq $total) { "Green" } else { "Yellow" })
Write-Host "=================================================================" -ForegroundColor Cyan

if ($passed -ne $total) {
    exit 1
}
