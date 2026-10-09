<#
.SYNOPSIS
    Checks an inbound inter-bank credit end to end, and that a repeated message credits once.
.DESCRIPTION
    A partner bank (BANKB) sends one transfer to the customer in the Postman environment file:

      gateway -> transaction-service -> orchestration (O4) -> ledger (L8) -> Oracle

    The script signs the request the way a partner does (HMAC-SHA256 over "<timestamp>.<body>"),
    reads the customer's balance before and after, and expects it to rise by exactly the amount.
    It then sends the same message again and expects the same answer and no second credit.

    It credits 150.00 to a seeded account from the settlement account 9005, so run it on a
    test stack. The shared secret is read from the running transaction-service container, or,
    when Compose gives it none, from the default in that service's application.properties.
    Sign-in names and passwords are read from the Postman environment file.
#>

[CmdletBinding()]
param(
    [string]$EnvironmentFile = "postman/CooBS_Local.postman_environment.json",
    [string]$BankCode = "BANKB",
    [string]$Amount = "150.00"
)

$ErrorActionPreference = "Stop"

$vars = @{}
(Get-Content $EnvironmentFile -Raw | ConvertFrom-Json).values | ForEach-Object { $vars[$_.key] = $_.value }
$baseUrl = $vars.baseUrl
$utf8 = New-Object System.Text.UTF8Encoding $false

# The partner's shared secret, without writing it anywhere.
$secret = & { $ErrorActionPreference = "Continue"; docker exec transaction-service printenv "INTERBANK_${BankCode}_SECRET" 2>$null }
if (-not $secret) {
    $properties = Get-Content (Join-Path $PSScriptRoot "..\backend\transaction-service\src\main\resources\application.properties")
    $line = $properties | Where-Object { $_.StartsWith("app.interbank.partners.$BankCode.shared-secret=") } | Select-Object -First 1
    # The line reads  ...shared-secret=${VARIABLE:default}
    if ($line -notmatch ':(?<default>[^:}]+)\}\s*$') { throw "No shared secret found for $BankCode" }
    $secret = $Matches["default"]
}

function Get-Balance([string]$token) {
    $accounts = Invoke-RestMethod -Uri "$baseUrl/api/v1/accounts/my-accounts" -Headers @{ Authorization = "Bearer $token" }
    if ($accounts.content) { $accounts = $accounts.content }
    return @($accounts)[0]
}

# Sends the body as the exact bytes that were signed. Returns the status code and the parsed answer.
function Send-Inbound([string]$body) {
    $timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    $hmac = New-Object System.Security.Cryptography.HMACSHA256 (, $utf8.GetBytes($secret))
    $signature = -join ($hmac.ComputeHash($utf8.GetBytes("$timestamp.$body")) | ForEach-Object { $_.ToString("x2") })
    $headers = @{ "X-Bank-Code" = $BankCode; "X-Timestamp" = $timestamp; "X-Signature" = $signature }
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$baseUrl/api/v1/interbank/transfers" `
            -Headers $headers -ContentType "application/json" -Body $utf8.GetBytes($body)
        return @{ Status = [int]$response.StatusCode; Answer = ($response.Content | ConvertFrom-Json) }
    } catch [System.Net.WebException] {
        if (-not $_.Exception.Response) { throw }
        $reader = New-Object System.IO.StreamReader ($_.Exception.Response.GetResponseStream())
        $text = $reader.ReadToEnd()
        $answer = try { $text | ConvertFrom-Json } catch { $text }
        return @{ Status = [int]$_.Exception.Response.StatusCode; Answer = $answer }
    }
}

$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/auth/customers/login" -ContentType "application/json" `
    -Body (@{ username = $vars.customerUsername; password = $vars.customerPassword } | ConvertTo-Json)
$token = $login.authData.accessToken

$before = Get-Balance $token
Write-Host "Beneficiary: account $($before.accountNumber), available balance $($before.availableBalance)" -ForegroundColor Cyan

$messageId = [guid]::NewGuid().ToString()
$body = [ordered]@{
    messageId           = $messageId
    sourceBankCode      = $BankCode
    destinationBankCode = "COOBS"
    sender              = [ordered]@{ accountNumber = "$BankCode-000123"; name = "Stack Check Sender" }
    beneficiary         = [ordered]@{ accountNumber = $before.accountNumber; name = "Stack Check Beneficiary" }
    amount              = $Amount
    currency            = "PHP"
    purpose             = "Inbound inter-bank stack check"
    senderReference     = "CHK-" + $messageId.Substring(0, 8)
    createdAt           = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
} | ConvertTo-Json -Compress -Depth 4

$failures = @()

Write-Host "1. Sending message $messageId for PHP $Amount"
$first = Send-Inbound $body
Write-Host "   HTTP $($first.Status): $($first.Answer.status) $($first.Answer.receiverReference) $($first.Answer.reasonCode) $($first.Answer.reasonMessage)"
if ($first.Status -ne 200 -or $first.Answer.status -ne "ACCEPTED") { $failures += "the transfer was not accepted" }

$afterFirst = Get-Balance $token
$credited = [decimal]$afterFirst.availableBalance - [decimal]$before.availableBalance
Write-Host "   Balance is now $($afterFirst.availableBalance) (change: $credited)"
if ($credited -ne [decimal]$Amount) { $failures += "the balance changed by $credited, expected $Amount" }

Write-Host "2. Sending the same message again"
$second = Send-Inbound $body
Write-Host "   HTTP $($second.Status): $($second.Answer.status) $($second.Answer.receiverReference) $($second.Answer.reasonCode)"
if ($second.Answer.status -ne $first.Answer.status) { $failures += "the repeat was answered $($second.Answer.status), the first $($first.Answer.status)" }
if ($first.Answer.receiverReference -and $second.Answer.receiverReference -ne $first.Answer.receiverReference) {
    $failures += "the repeat got a different receiver reference"
}

$afterSecond = Get-Balance $token
$creditedTwice = [decimal]$afterSecond.availableBalance - [decimal]$afterFirst.availableBalance
Write-Host "   Balance is now $($afterSecond.availableBalance) (change: $creditedTwice)"
if ($creditedTwice -ne 0) { $failures += "the repeat moved the balance by $creditedTwice" }

if (-not $failures) {
    Write-Host "PASS: credited $Amount once, and the repeated message changed nothing" -ForegroundColor Green
    exit 0
}
Write-Host "FAIL:" -ForegroundColor Red
$failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
Write-Host "Logs: docker logs --tail 80 transaction-service ; docker logs --tail 80 orchestration-service ; docker logs --tail 80 ledger-service"
exit 1
