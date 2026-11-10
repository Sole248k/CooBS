<#
.SYNOPSIS
    Shows two risk decisions end to end that the E2E suite does not: a decline on the fraud
    score, and failing closed when the risk engine does not answer.
.DESCRIPTION
    Burst
      The seeded TIER_1 member (grace_pangilinan) sends five small transfers in a row. The
      score is 0.35 x amount/limit + 0.35 x velocity + 0.30 x tier. Her tier alone gives 0.30;
      four approved transfers in ten minutes take velocity to its maximum, so the fifth scores
      0.65, over the 0.60 line. Expected: four POSTED, the fifth FAILED with RISK_DECLINED at
      stage 4, no hold, and a balance down by exactly the four that posted.

    EngineDown
      The risk engine is stopped and one transfer is sent. Expected: FAILED with
      RISK_UNAVAILABLE at stage 4, nothing after it, balance unchanged. The engine is started
      again and, once the orchestrator has warmed it, a transfer must post.

    Both move a few pesos between seeded accounts and the second stops a container for about a
    minute, so run this on a test stack and not during E2E. The velocity counter of the burst
    member is cleared before and after, so the script can be run again at once.
    Sign-in names and passwords are read from the Postman environment file.
#>

[CmdletBinding()]
param(
    [ValidateSet("All", "Burst", "EngineDown")] [string]$Scenario = "All",
    [string]$EnvironmentFile = "postman/CooBS_Local.postman_environment.json",
    [string]$BurstUsername = "grace_pangilinan"
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

function Get-Account([string]$token) {
    $accounts = Invoke-RestMethod -Uri "$baseUrl/api/v1/accounts/my-accounts" -Headers @{ Authorization = "Bearer $token" }
    # A page keeps its rows in "content"; a plain list has none. Asked of a list of two, .content
    # gives two nulls, which PowerShell counts as true, so a list is never asked.
    if ($accounts -isnot [array] -and $accounts.content) { $accounts = $accounts.content }
    return @($accounts)[0]
}

# Sends one transfer and waits for it to leave INITIATED and PROCESSING. Returns the transaction.
function Send-Transfer([string]$token, [long]$sourceAccountId, [string]$destinationAccountNumber, [string]$amount) {
    $headers = @{ Authorization = "Bearer $token"; "Idempotency-Key" = [guid]::NewGuid().ToString() }
    $body = [ordered]@{
        sourceAccountId          = $sourceAccountId
        destinationAccountNumber = $destinationAccountNumber
        destinationBankCode      = "COOBS"
        amount                   = $amount
        currency                 = "PHP"
        category                 = "TRANSFER"
        remarks                  = "Risk scenario check"
    } | ConvertTo-Json
    $accepted = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/transactions/transfers" -Headers $headers `
        -ContentType "application/json" -Body $body
    $deadline = (Get-Date).AddSeconds(45)
    do {
        Start-Sleep -Milliseconds 700
        $transaction = Invoke-RestMethod -Uri "$baseUrl/api/v1/transactions/$($accepted.transactionId)" `
            -Headers @{ Authorization = "Bearer $token" }
    } while ($transaction.status -in "INITIATED", "PROCESSING" -and (Get-Date) -lt $deadline)
    return $transaction
}

# The staff timeline: which stage failed, and whether anything ran after it.
function Get-Stages([string]$tellerToken, [string]$transactionId) {
    return @((Invoke-RestMethod -Uri "$baseUrl/api/v1/transactions/$transactionId/timeline" `
        -Headers @{ Authorization = "Bearer $tellerToken" }).stages)
}

function Assert-StoppedAtStage4([object[]]$stages, [string]$what) {
    $failed = @($stages | Where-Object { $_.outcome -eq "FAILED" })
    if ($failed.Count -ne 1 -or $failed[0].stageNo -ne 4) {
        return "${what}: expected stage 4 alone to have failed, got " + (($failed | ForEach-Object { "$($_.stageNo) $($_.stageName)" }) -join ", ")
    }
    $later = @($stages | Where-Object { $_.stageNo -gt 4 -and $_.outcome -eq "PASSED" })
    if ($later.Count) { return "${what}: stages after 4 ran, so a hold may have been placed" }
    return $null
}

function Invoke-Docker([string[]]$arguments) {
    $ErrorActionPreference = "Continue"
    docker @arguments 2>$null | Out-Null
}

$tellerToken = Get-Token "/api/v1/auth/staff/login" $vars.tellerUsername $vars.tellerPassword

# -----------------------------------------------------------------------------
if ($Scenario -in "All", "Burst") {
    Write-Host "`n== Burst: five transfers in a row by the TIER_1 member ==" -ForegroundColor Cyan
    $token = Get-Token "/api/v1/auth/customers/login" $BurstUsername $vars.customerPassword
    $customerId = (Invoke-RestMethod -Uri "$baseUrl/api/v1/customers/me" -Headers @{ Authorization = "Bearer $token" }).customerId
    $velocityKey = "risk:velocity:$customerId"
    Invoke-Docker @("exec", "redis-cache", "redis-cli", "del", $velocityKey)

    $account = Get-Account $token
    $before = [decimal]$account.availableBalance
    Write-Host "$BurstUsername, customer $customerId, account $($account.accountNumber), balance $before"

    try {
        foreach ($n in 1..5) {
            $transaction = Send-Transfer $token $account.accountId "ACC_10000001" "1.00"
            Write-Host ("  transfer {0}: {1} {2}" -f $n, $transaction.status, $transaction.failureCode)
            if ($n -le 4 -and $transaction.status -ne "POSTED") {
                $failures += "burst: transfer $n should have posted, ended $($transaction.status) $($transaction.failureCode)"
            }
            if ($n -eq 5) {
                if ($transaction.status -ne "FAILED" -or $transaction.failureCode -ne "RISK_DECLINED") {
                    $failures += "burst: transfer 5 should have ended FAILED with RISK_DECLINED, ended $($transaction.status) $($transaction.failureCode)"
                }
                $problem = Assert-StoppedAtStage4 (Get-Stages $tellerToken $transaction.transactionId) "burst"
                if ($problem) { $failures += $problem }
            }
        }
        $moved = $before - [decimal](Get-Account $token).availableBalance
        Write-Host "  balance moved by $moved"
        if ($moved -ne 4) { $failures += "burst: the balance moved by $moved, expected 4.00 (the declined transfer must not move money)" }
    } finally {
        Invoke-Docker @("exec", "redis-cache", "redis-cli", "del", $velocityKey)
    }
}

# -----------------------------------------------------------------------------
if ($Scenario -in "All", "EngineDown") {
    Write-Host "`n== EngineDown: a transfer while the risk engine is stopped ==" -ForegroundColor Cyan
    $token = Get-Token "/api/v1/auth/customers/login" $vars.customerUsername $vars.customerPassword
    $account = Get-Account $token
    $before = [decimal]$account.availableBalance
    Write-Host "$($vars.customerUsername), account $($account.accountNumber), balance $before"

    try {
        Invoke-Docker @("stop", "risk-engine")
        $transaction = Send-Transfer $token $account.accountId "ACC_10000002" "1.00"
        Write-Host "  with the engine stopped: $($transaction.status) $($transaction.failureCode)"
        if ($transaction.status -ne "FAILED" -or $transaction.failureCode -ne "RISK_UNAVAILABLE") {
            $failures += "engine down: expected FAILED with RISK_UNAVAILABLE, got $($transaction.status) $($transaction.failureCode)"
        }
        $problem = Assert-StoppedAtStage4 (Get-Stages $tellerToken $transaction.transactionId) "engine down"
        if ($problem) { $failures += $problem }
        $moved = $before - [decimal](Get-Account $token).availableBalance
        if ($moved -ne 0) { $failures += "engine down: the balance moved by $moved although the transfer failed" }
    } finally {
        Invoke-Docker @("start", "risk-engine")
    }

    Write-Host "  engine started again; waiting for it to be healthy and for the orchestrator to warm it"
    $deadline = (Get-Date).AddSeconds(180)
    do {
        Start-Sleep -Seconds 5
        $health = docker inspect risk-engine --format "{{.State.Health.Status}}"
    } while ($health -ne "healthy" -and (Get-Date) -lt $deadline)
    # Two keep-warm rounds of the orchestrator (one every 20 seconds).
    Start-Sleep -Seconds 45

    $transaction = Send-Transfer $token $account.accountId "ACC_10000002" "1.00"
    Write-Host "  after the restart: $($transaction.status) $($transaction.failureCode)"
    if ($transaction.status -ne "POSTED") {
        $failures += "engine down: after the restart a transfer should post, ended $($transaction.status) $($transaction.failureCode)"
    }
}

Write-Host ""
if (-not $failures) {
    Write-Host "PASS ($Scenario)" -ForegroundColor Green
    exit 0
}
Write-Host "FAIL:" -ForegroundColor Red
$failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
Write-Host "Logs: docker logs --tail 80 orchestration-service ; docker logs --tail 80 risk-engine"
exit 1
