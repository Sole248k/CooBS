<#
.SYNOPSIS
    Shows a transfer to another bank end to end: accepted by the partner and posted, refused by
    the partner and released, and refused at the door for a bank that is not a partner.
.DESCRIPTION
    BANKB is seeded as a partner with no address, which makes it our own loopback: the inter-bank
    gateway in transaction-service stands in for the other bank. It accepts every transfer except
    to an account number beginning 0000 (contract section 14).

    Accepted
      The baseline member sends PHP 1.00 to account 1000-2000-3000 at BANKB. Expected: POSTED,
      all stages passed up to 8, stage 7 AUTHORIZED carrying the partner's answer, the sender's
      balance down by exactly 1.00.

    Refused by the partner
      The same member sends PHP 1.00 to account 0000-1111-2222 at BANKB. Expected: FAILED with
      INTERBANK_REJECTED at stage 7, the hold released, the balance unchanged by it.

    Not a partner
      A transfer to bank BPI. Expected: 422 INTERBANK_REJECTED at once; no transaction is made.

    The script moves one peso from a seeded account to the BANKB settlement account.
    Sign-in names and passwords are read from the Postman environment file.
#>

[CmdletBinding()]
param(
    [string]$EnvironmentFile = "postman/CooBS_Local.postman_environment.json",
    [string]$PartnerBank = "BANKB"
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

function New-TransferBody([long]$sourceAccountId, [string]$bankCode, [string]$accountNumber) {
    return [ordered]@{
        sourceAccountId          = $sourceAccountId
        destinationAccountNumber = $accountNumber
        destinationBankCode      = $bankCode
        beneficiaryName          = "Ana Lim"
        amount                   = "1.00"
        currency                 = "PHP"
        category                 = "TRANSFER"
        remarks                  = "Outbound inter-bank check"
    } | ConvertTo-Json
}

# Sends one transfer and waits for it to leave INITIATED and PROCESSING. Returns the transaction.
function Send-Transfer([string]$token, [string]$body) {
    $headers = @{ Authorization = "Bearer $token"; "Idempotency-Key" = [guid]::NewGuid().ToString() }
    $accepted = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/transactions/transfers" -Headers $headers `
        -ContentType "application/json" -Body $body
    $deadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Milliseconds 700
        $transaction = Invoke-RestMethod -Uri "$baseUrl/api/v1/transactions/$($accepted.transactionId)" `
            -Headers @{ Authorization = "Bearer $token" }
    } while ($transaction.status -in "INITIATED", "PROCESSING" -and (Get-Date) -lt $deadline)
    return $transaction
}

function Get-Stages([string]$tellerToken, [string]$transactionId) {
    return @((Invoke-RestMethod -Uri "$baseUrl/api/v1/transactions/$transactionId/timeline" `
        -Headers @{ Authorization = "Bearer $tellerToken" }).stages)
}

$tellerToken = Get-Token "/api/v1/auth/staff/login" $vars.tellerUsername $vars.tellerPassword
$token = Get-Token "/api/v1/auth/customers/login" $vars.customerUsername $vars.customerPassword
$account = Get-Account $token
$before = [decimal]$account.availableBalance
Write-Host "$($vars.customerUsername), account $($account.accountNumber), balance $before"

# -----------------------------------------------------------------------------
Write-Host "`n== Accepted: 1.00 to 1000-2000-3000 at $PartnerBank ==" -ForegroundColor Cyan
$transaction = Send-Transfer $token (New-TransferBody $account.accountId $PartnerBank "1000-2000-3000")
Write-Host "  $($transaction.status) $($transaction.failureCode)"
if ($transaction.status -ne "POSTED") {
    $failures += "accepted: expected POSTED, ended $($transaction.status) $($transaction.failureCode)"
} else {
    $stage7 = Get-Stages $tellerToken $transaction.transactionId | Where-Object { $_.stageNo -eq 7 }
    Write-Host "  stage 7 AUTHORIZED: $($stage7.outcome), $($stage7.detail)"
    if ($stage7.outcome -ne "PASSED" -or "$($stage7.detail)" -notmatch "accepted by $PartnerBank") {
        $failures += "accepted: stage 7 should have passed with the partner's answer, it says '$($stage7.outcome) $($stage7.detail)'"
    }
}
$afterAccepted = [decimal](Get-Account $token).availableBalance
Write-Host "  balance moved by $($before - $afterAccepted)"
if (($before - $afterAccepted) -ne 1) { $failures += "accepted: the balance moved by $($before - $afterAccepted), expected 1.00" }

# -----------------------------------------------------------------------------
Write-Host "`n== Refused by the partner: 1.00 to 0000-1111-2222 at $PartnerBank ==" -ForegroundColor Cyan
$transaction = Send-Transfer $token (New-TransferBody $account.accountId $PartnerBank "0000-1111-2222")
Write-Host "  $($transaction.status) $($transaction.failureCode)"
if ($transaction.status -ne "FAILED" -or $transaction.failureCode -ne "INTERBANK_REJECTED") {
    $failures += "refused: expected FAILED with INTERBANK_REJECTED, ended $($transaction.status) $($transaction.failureCode)"
}
$stages = Get-Stages $tellerToken $transaction.transactionId
$failed = @($stages | Where-Object { $_.outcome -eq "FAILED" })
if ($failed.Count -ne 1 -or $failed[0].stageNo -ne 7) {
    $failures += "refused: expected stage 7 alone to have failed, got " + (($failed | ForEach-Object { "$($_.stageNo) $($_.stageName)" }) -join ", ")
} else {
    Write-Host "  stage 7 AUTHORIZED failed: $($failed[0].detail)"
    if ("$($failed[0].detail)" -notmatch "hold released") {
        $failures += "refused: the timeline does not say the hold was released: '$($failed[0].detail)'"
    }
}
$afterRefused = [decimal](Get-Account $token).availableBalance
if ($afterRefused -ne $afterAccepted) {
    $failures += "refused: the balance moved by $($afterAccepted - $afterRefused) although the partner refused the transfer"
}

# -----------------------------------------------------------------------------
Write-Host "`n== Not a partner: a transfer to bank BPI ==" -ForegroundColor Cyan
$status = 0
$errorCode = ""
try {
    $headers = @{ Authorization = "Bearer $token"; "Idempotency-Key" = [guid]::NewGuid().ToString() }
    Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/transactions/transfers" -Headers $headers `
        -ContentType "application/json" -Body (New-TransferBody $account.accountId "BPI" "1000-2000-3000") | Out-Null
    $status = 202
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    if ($_.ErrorDetails.Message) { $errorCode = ($_.ErrorDetails.Message | ConvertFrom-Json).errorCode }
}
Write-Host "  HTTP $status $errorCode"
if ($status -ne 422) { $failures += "not a partner: expected HTTP 422, got $status" }
if ($errorCode -and $errorCode -ne "INTERBANK_REJECTED") { $failures += "not a partner: expected INTERBANK_REJECTED, got $errorCode" }

Write-Host ""
if (-not $failures) {
    Write-Host "PASS" -ForegroundColor Green
    exit 0
}
Write-Host "FAIL:" -ForegroundColor Red
$failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
Write-Host "Logs: docker logs --tail 80 orchestration-service ; docker logs --tail 80 transaction-service"
exit 1
