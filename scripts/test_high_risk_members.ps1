<#
.SYNOPSIS
    Shows that the customer's own risk score is a floor under a transfer's score: the two
    members seeded with a high score are declined whatever they send.
.DESCRIPTION
    andrea_reyes is seeded with a risk score of 0.92 and jose_bautista with 0.72
    (database/seed/SEED_REFERENCE.md). Each sends PHP 1.00, which on its own scores about 0.03.

    Expected for both: FAILED with RISK_DECLINED at stage 4, nothing after it, no hold, balance
    unchanged. The staff timeline gives the reason at stage 4: HIGH_RISK for the score above
    0.85, REVIEW_REQUIRED for the score from 0.60 to 0.85, each followed by
    CUSTOMER_RISK_RATING.

    As a control the baseline member (the customer in the Postman environment, rated 0.15)
    sends PHP 1.00 and must post, so the script moves one peso between seeded accounts.
    Sign-in names and passwords are read from the Postman environment file.
#>

[CmdletBinding()]
param(
    [string]$EnvironmentFile = "postman/CooBS_Local.postman_environment.json"
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
    if ($accounts.content) { $accounts = $accounts.content }
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
        remarks                  = "High-risk member check"
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

$tellerToken = Get-Token "/api/v1/auth/staff/login" $vars.tellerUsername $vars.tellerPassword

$members = @(
    @{ Username = "andrea_reyes"; Rated = "0.92"; Reason = "HIGH_RISK" },
    @{ Username = "jose_bautista"; Rated = "0.72"; Reason = "REVIEW_REQUIRED" }
)

foreach ($member in $members) {
    $who = $member.Username
    Write-Host "`n== $who, rated $($member.Rated): expected $($member.Reason) ==" -ForegroundColor Cyan
    $token = Get-Token "/api/v1/auth/customers/login" $who $vars.customerPassword
    $account = Get-Account $token
    $before = [decimal]$account.availableBalance
    Write-Host "  account $($account.accountNumber), balance $before"

    $transaction = Send-Transfer $token $account.accountId "ACC_10000001" "1.00"
    Write-Host "  transfer of 1.00: $($transaction.status) $($transaction.failureCode)"
    if ($transaction.status -ne "FAILED" -or $transaction.failureCode -ne "RISK_DECLINED") {
        $failures += "${who}: expected FAILED with RISK_DECLINED, ended $($transaction.status) $($transaction.failureCode)"
    }

    # The staff timeline: which stage failed, why, and whether anything ran after it.
    $stages = @((Invoke-RestMethod -Uri "$baseUrl/api/v1/transactions/$($transaction.transactionId)/timeline" `
        -Headers @{ Authorization = "Bearer $tellerToken" }).stages)
    $failed = @($stages | Where-Object { $_.outcome -eq "FAILED" })
    if ($failed.Count -ne 1 -or $failed[0].stageNo -ne 4) {
        $failures += "${who}: expected stage 4 alone to have failed, got " + (($failed | ForEach-Object { "$($_.stageNo) $($_.stageName)" }) -join ", ")
    } else {
        $reasons = @("$($failed[0].detail)" -split ",")
        Write-Host "  stage 4 FRAUD_CHECK failed: $($failed[0].detail)"
        if ($reasons[0] -ne $member.Reason) {
            $failures += "${who}: expected the reason $($member.Reason) first, the timeline says '$($failed[0].detail)'"
        }
        if ($reasons -notcontains "CUSTOMER_RISK_RATING") {
            $failures += "${who}: the timeline does not say CUSTOMER_RISK_RATING, so the decline did not come from the member's own score"
        }
    }
    if (@($stages | Where-Object { $_.stageNo -gt 4 -and $_.outcome -eq "PASSED" }).Count) {
        $failures += "${who}: stages after 4 ran, so a hold may have been placed"
    }

    $moved = $before - [decimal](Get-Account $token).availableBalance
    if ($moved -ne 0) { $failures += "${who}: the balance moved by $moved although the transfer was declined" }
}

Write-Host "`n== Control: $($vars.customerUsername), a low-rated member, still posts ==" -ForegroundColor Cyan
$token = Get-Token "/api/v1/auth/customers/login" $vars.customerUsername $vars.customerPassword
$account = Get-Account $token
$transaction = Send-Transfer $token $account.accountId "ACC_10000002" "1.00"
Write-Host "  transfer of 1.00: $($transaction.status) $($transaction.failureCode)"
if ($transaction.status -ne "POSTED") {
    $failures += "control: a low-rated member should post, ended $($transaction.status) $($transaction.failureCode)"
}

Write-Host ""
if (-not $failures) {
    Write-Host "PASS" -ForegroundColor Green
    exit 0
}
Write-Host "FAIL:" -ForegroundColor Red
$failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
Write-Host "Logs: docker logs --tail 80 orchestration-service ; docker logs --tail 80 risk-engine"
exit 1
