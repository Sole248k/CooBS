<#
.SYNOPSIS
    Checks three things about end of day that the E2E suite does not: a second month-end run sends
    no second interest notice, the run report shows what each step stored, and a run that was cut
    off by a restart does not block the next one.
.DESCRIPTION
    One notice per credit
      Runs end of day twice for the last day of the previous month. The ledger pays the interest
      once, so the member's "Monthly Interest Credited" notices must not grow on the second run.

    The report
      Prints the steps of the second run. Every completed step must carry the total and the
      message it stored itself, and step 5 must say what it compared.

    Recovery
      Puts a batch run in the table as RUNNING, as a run looks when the service dies in the middle
      of it, restarts batch-service, and expects the row to read HALTED. The row is removed again.

    It credits interest to the seeded accounts and restarts one container for about a minute.
    Run it on a test stack, with nobody else using it. Sign-in names and passwords are read from
    the Postman environment file.
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\test_batch_report_and_recovery.ps1
#>

[CmdletBinding()]
param(
    [string]$EnvironmentFile = "postman/CooBS_Local.postman_environment.json",
    [string]$PostgresContainer = "postgres-audit-db",
    [string]$BatchContainer = "batch-service"
)

$ErrorActionPreference = "Stop"

$vars = @{}
(Get-Content $EnvironmentFile -Raw | ConvertFrom-Json).values | ForEach-Object { $vars[$_.key] = $_.value }
$baseUrl = $vars.baseUrl
$failures = @()

function Get-Token([string]$path, [string]$username, [string]$password) {
    $body = @{ username = $username; password = $password } | ConvertTo-Json
    return (Invoke-RestMethod -Method Post -Uri "$baseUrl$path" -ContentType "application/json" -Body $body).authData.accessToken
}

function Get-InterestNotices([string]$token) {
    $page = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/v1/notifications/my-notifications?size=50" `
        -Headers @{ Authorization = "Bearer $token" }
    return ([regex]::Matches($page.Content, "Monthly Interest Credited")).Count
}

# Triggers end of day for the date and waits for the run to end. Returns the run with its steps.
function Invoke-EndOfDay([string]$token, [string]$businessDate) {
    $run = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/batch/eod/trigger" `
        -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" `
        -Body (@{ businessDate = $businessDate } | ConvertTo-Json)
    $deadline = (Get-Date).AddSeconds(90)
    do {
        Start-Sleep -Seconds 2
        $details = Invoke-RestMethod -Uri "$baseUrl/api/v1/batch/eod/runs/$($run.batchRunId)" `
            -Headers @{ Authorization = "Bearer $token" }
    } while ($details.status -eq "RUNNING" -and (Get-Date) -lt $deadline)
    return $details
}

function Invoke-Sql([string]$sql) {
    $out = docker exec $PostgresContainer psql -U postgres -d audit_store -t -A -q -c $sql
    if ($LASTEXITCODE -ne 0) { throw "The audit database could not be reached. Is the container '$PostgresContainer' running?" }
    return (($out | Where-Object { $_ }) -join "`n").Trim()
}

$adminToken = Get-Token "/api/v1/auth/staff/login" $vars.staffUsername $vars.staffPassword
$customerToken = Get-Token "/api/v1/auth/customers/login" $vars.customerUsername $vars.customerPassword
$today = Get-Date
$monthEnd = (Get-Date -Year $today.Year -Month $today.Month -Day 1).AddDays(-1).ToString("yyyy-MM-dd")

# -----------------------------------------------------------------------------
Write-Host "`n== One notice per credit: end of day for $monthEnd, twice ==" -ForegroundColor Cyan
$first = Invoke-EndOfDay $adminToken $monthEnd
Write-Host "  run $($first.batchRunId) ended $($first.status)"
# The notice is written by a Kafka consumer, so allow it a moment after the run ends.
$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Seconds 2
    $afterFirst = Get-InterestNotices $customerToken
} while ($afterFirst -eq 0 -and (Get-Date) -lt $deadline)
Write-Host "  $($vars.customerUsername) has $afterFirst interest notice(s) after the first run"
if ($afterFirst -eq 0) {
    $failures += "notice: no 'Monthly Interest Credited' notice after a month-end run"
}

$second = Invoke-EndOfDay $adminToken $monthEnd
Write-Host "  run $($second.batchRunId) ended $($second.status)"
Start-Sleep -Seconds 10
$afterSecond = Get-InterestNotices $customerToken
Write-Host "  $($vars.customerUsername) has $afterSecond interest notice(s) after the second run"
if ($afterSecond -ne $afterFirst) {
    $failures += "notice: the second run changed the count from $afterFirst to $afterSecond; one credit must give one notice"
}
if ($first.status -ne "COMPLETED" -or $second.status -ne "COMPLETED") {
    $failures += "notice: the runs ended $($first.status) and $($second.status), expected COMPLETED twice"
}

# -----------------------------------------------------------------------------
Write-Host "`n== The report of run $($second.batchRunId) ==" -ForegroundColor Cyan
$second.steps | Sort-Object stepNo | Format-Table stepNo, stepName, status, itemsProcessed, totalAmount, message -AutoSize |
    Out-String -Width 200 | Write-Host
foreach ($step in $second.steps) {
    if ($step.status -ne "COMPLETED") { continue }
    if ($null -eq $step.totalAmount -or "$($step.totalAmount)" -eq "") {
        $failures += "report: step $($step.stepName) has no total; the report must show what the step stored"
    }
    if (-not $step.message) {
        $failures += "report: step $($step.stepName) has no message"
    }
}
$reconciliation = $second.steps | Where-Object { $_.stepName -eq "GL_RECONCILIATION" }
if ($reconciliation.message -notlike "Debits equal credits (*journal entries*") {
    $failures += "report: GL_RECONCILIATION says '$($reconciliation.message)', expected the number of journal entries it compared"
}

# -----------------------------------------------------------------------------
Write-Host "`n== Recovery: a run left RUNNING is halted when $BatchContainer starts ==" -ForegroundColor Cyan
$stuck = Invoke-Sql "INSERT INTO batch.batch_run (business_date, triggered_by, status) VALUES (CURRENT_DATE - 400, 1, 'RUNNING') RETURNING batch_run_id;"
if ($stuck -notmatch '^\d+$') { throw "The test row could not be added to batch.batch_run (got '$stuck')." }
Write-Host "  run $stuck added as RUNNING; restarting $BatchContainer"
try {
    docker restart $BatchContainer | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "The container '$BatchContainer' could not be restarted." }
    $deadline = (Get-Date).AddSeconds(120)
    do {
        Start-Sleep -Seconds 3
        $status = Invoke-Sql "SELECT status FROM batch.batch_run WHERE batch_run_id = $stuck;"
    } while ($status -eq "RUNNING" -and (Get-Date) -lt $deadline)
    Write-Host "  run $stuck now reads $status"
    if ($status -ne "HALTED") {
        $failures += "recovery: run $stuck reads $status two minutes after the restart, expected HALTED; it would refuse every later end of day"
    }
} finally {
    Invoke-Sql "DELETE FROM batch.batch_step WHERE batch_run_id = $stuck; DELETE FROM batch.batch_run WHERE batch_run_id = $stuck;" | Out-Null
    Write-Host "  run $stuck removed again"
}

# Leave the stack usable: wait until the gateway reaches batch-service again.
$deadline = (Get-Date).AddSeconds(120)
do {
    Start-Sleep -Seconds 3
    try {
        Invoke-RestMethod -Uri "$baseUrl/api/v1/batch/eod/runs" -Headers @{ Authorization = "Bearer $adminToken" } | Out-Null
        $up = $true
    } catch {
        $up = $false
    }
} while (-not $up -and (Get-Date) -lt $deadline)
if (-not $up) {
    $failures += "recovery: $BatchContainer did not answer through the gateway two minutes after the restart"
}

Write-Host ""
if (-not $failures) {
    Write-Host "PASS" -ForegroundColor Green
    exit 0
}
Write-Host "FAIL:" -ForegroundColor Red
$failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
Write-Host "Logs: docker logs --tail 80 $BatchContainer ; docker logs --tail 80 notification-service"
exit 1
