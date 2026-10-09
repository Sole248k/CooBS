<#
.SYNOPSIS
    Shows that the daily limit is a reservation: an approved transfer that does not post gives
    its amount back, and one that posts keeps exactly what it sent.
.DESCRIPTION
    The risk engine reserves the amount when it approves a transfer and the saga tells it when
    the transfer does not post (R2, contract section 9). What is reserved cannot be read through
    the API, so this check reads the engine's counter in Redis before and after:

      risk:daily:reserved:<customerId>:<business date in Manila>   in ten-thousandths of a peso

    Given back
      The low-balance member (liza_mercado) sends more than she has. Risk approves and reserves;
      the ledger refuses the hold. Expected: FAILED with INSUFFICIENT_FUNDS, the counter back to
      what it was, and no reservation left for that transaction.

    Kept
      The baseline member sends PHP 1.00 that posts. Expected: the counter up by exactly 10000.

    It moves one peso between seeded accounts. Run it while nothing else is sending transfers
    for these two members, or the counters will move for another reason.
    Sign-in names and passwords are read from the Postman environment file.
#>

[CmdletBinding()]
param(
    [string]$EnvironmentFile = "postman/CooBS_Local.postman_environment.json",
    [string]$RedisContainer = "redis-cache",
    [string]$LowBalanceUsername = "liza_mercado"
)

$ErrorActionPreference = "Stop"

$vars = @{}
(Get-Content $EnvironmentFile -Raw | ConvertFrom-Json).values | ForEach-Object { $vars[$_.key] = $_.value }
$baseUrl = $vars.baseUrl
$failures = @()
# The engine's day starts at midnight in Manila (UTC+8, no daylight saving).
$businessDate = (Get-Date).ToUniversalTime().AddHours(8).ToString("yyyy-MM-dd")

function Get-Token([string]$path, [string]$username, [string]$password) {
    $body = @{ username = $username; password = $password } | ConvertTo-Json
    return (Invoke-RestMethod -Method Post -Uri "$baseUrl$path" -ContentType "application/json" -Body $body).authData.accessToken
}

function Get-Account([string]$token) {
    $accounts = Invoke-RestMethod -Uri "$baseUrl/api/v1/accounts/my-accounts" -Headers @{ Authorization = "Bearer $token" }
    if ($accounts.content) { $accounts = $accounts.content }
    return @($accounts)[0]
}

function Get-CustomerId([string]$token) {
    return (Invoke-RestMethod -Uri "$baseUrl/api/v1/customers/me" -Headers @{ Authorization = "Bearer $token" }).customerId
}

# What the engine holds reserved for a customer today, in ten-thousandths of a peso. No key means nothing.
function Get-Reserved([long]$customerId) {
    $value = docker exec $RedisContainer redis-cli GET "risk:daily:reserved:${customerId}:$businessDate"
    if ($LASTEXITCODE -ne 0) { throw "Redis could not be read. Is the container '$RedisContainer' running?" }
    if ("$value" -match '^-?\d+$') { return [long]"$value" }
    return 0
}

function Send-Transfer([string]$token, [long]$sourceAccountId, [string]$destinationAccountNumber, [string]$amount) {
    $headers = @{ Authorization = "Bearer $token"; "Idempotency-Key" = [guid]::NewGuid().ToString() }
    $body = [ordered]@{
        sourceAccountId          = $sourceAccountId
        destinationAccountNumber = $destinationAccountNumber
        destinationBankCode      = "COOBS"
        amount                   = $amount
        currency                 = "PHP"
        category                 = "TRANSFER"
        remarks                  = "Daily limit reservation check"
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

# -----------------------------------------------------------------------------
Write-Host "`n== Given back: $LowBalanceUsername sends more than she has ==" -ForegroundColor Cyan
$token = Get-Token "/api/v1/auth/customers/login" $LowBalanceUsername $vars.customerPassword
$customerId = Get-CustomerId $token
$account = Get-Account $token
$amount = ([decimal]$account.availableBalance + 100).ToString("0.00", [cultureinfo]::InvariantCulture)
$before = Get-Reserved $customerId
Write-Host "  customer $customerId, balance $($account.availableBalance), reserved today $before units; sending $amount"

$transaction = Send-Transfer $token $account.accountId "ACC_10000002" $amount
Write-Host "  $($transaction.status) $($transaction.failureCode)"
if ($transaction.status -ne "FAILED" -or $transaction.failureCode -ne "INSUFFICIENT_FUNDS") {
    $failures += "given back: expected FAILED with INSUFFICIENT_FUNDS, ended $($transaction.status) $($transaction.failureCode)"
}
# The release follows the failure by one call; give it a moment.
$deadline = (Get-Date).AddSeconds(10)
do {
    Start-Sleep -Milliseconds 500
    $after = Get-Reserved $customerId
} while ($after -ne $before -and (Get-Date) -lt $deadline)
Write-Host "  reserved today after the failed transfer: $after units"
if ($after -ne $before) {
    $failures += "given back: $($after - $before) units are still reserved for a transfer that did not post"
}
$marker = docker exec $RedisContainer redis-cli EXISTS "risk:counted:$($transaction.transactionId)"
if ("$marker" -ne "0") {
    $failures += "given back: the reservation of transaction $($transaction.transactionId) is still recorded"
}

# -----------------------------------------------------------------------------
Write-Host "`n== Kept: $($vars.customerUsername) sends 1.00 that posts ==" -ForegroundColor Cyan
$token = Get-Token "/api/v1/auth/customers/login" $vars.customerUsername $vars.customerPassword
$customerId = Get-CustomerId $token
$account = Get-Account $token
$before = Get-Reserved $customerId
$transaction = Send-Transfer $token $account.accountId "ACC_10000002" "1.00"
$after = Get-Reserved $customerId
Write-Host "  customer ${customerId}: $($transaction.status) $($transaction.failureCode); reserved today $before -> $after units"
if ($transaction.status -ne "POSTED") {
    $failures += "kept: the transfer should have posted, ended $($transaction.status) $($transaction.failureCode)"
} elseif (($after - $before) -ne 10000) {
    $failures += "kept: the reserved amount moved by $($after - $before) units, expected exactly 10000 (PHP 1.00)"
}

Write-Host ""
if (-not $failures) {
    Write-Host "PASS" -ForegroundColor Green
    exit 0
}
Write-Host "FAIL:" -ForegroundColor Red
$failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
Write-Host "Logs: docker logs --tail 80 risk-engine ; docker logs --tail 80 orchestration-service"
exit 1
