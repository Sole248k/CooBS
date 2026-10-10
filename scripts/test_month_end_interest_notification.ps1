<#
.SYNOPSIS
    Checks that a month-end EOD run delivers the interest notification through Kafka.
.DESCRIPTION
    The interest step sends a notification only when the business date is the last day of a
    month, so the E2E suite (which runs EOD for today) never exercises that path. This script
    triggers EOD for the last day of the previous month, waits for the run to finish, and then
    looks for "Monthly Interest Credited" in the customer's notifications.

    batch-service publishes INTEREST_CREDIT on ledger.mutation.completed.v1 and
    notification-service writes the notification, so a PASS proves that path end to end.

    It credits interest to the seeded accounts. Run it on a test stack and reset afterwards.
    Sign-in names and passwords are read from the Postman environment file.
#>

[CmdletBinding()]
param(
    [string]$EnvironmentFile = "postman/CooBS_Local.postman_environment.json",
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"

$vars = @{}
(Get-Content $EnvironmentFile -Raw | ConvertFrom-Json).values | ForEach-Object { $vars[$_.key] = $_.value }
$baseUrl = $vars.baseUrl

function Get-Token([string]$path, [string]$username, [string]$password) {
    $body = @{ username = $username; password = $password } | ConvertTo-Json
    $response = Invoke-RestMethod -Method Post -Uri "$baseUrl$path" -ContentType "application/json" -Body $body
    return $response.authData.accessToken
}

# The member's interest notices, counted over every page of the inbox. After a day of checks the
# newest fifty notifications no longer reach back to them, so one page is not enough.
function Get-InterestNotices([string]$token) {
    $count = 0
    $page = 0
    do {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/v1/notifications/my-notifications?size=50&page=$page" `
            -Headers @{ Authorization = "Bearer $token" }
        $count += ([regex]::Matches($response.Content, "Monthly Interest Credited")).Count
        $pages = if ($response.Content -match '"totalPages"\s*:\s*(\d+)') { [int]$Matches[1] } else { 1 }
        $page++
    } while ($page -lt $pages -and $page -lt 40)
    return $count
}
$adminToken = Get-Token "/api/v1/auth/staff/login" $vars.staffUsername $vars.staffPassword
$customerToken = Get-Token "/api/v1/auth/customers/login" $vars.customerUsername $vars.customerPassword

$today = Get-Date
$businessDate = (Get-Date -Year $today.Year -Month $today.Month -Day 1).AddDays(-1).ToString("yyyy-MM-dd")
Write-Host "Triggering EOD for month-end business date $businessDate" -ForegroundColor Cyan

$run = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/batch/eod/trigger" `
    -Headers @{ Authorization = "Bearer $adminToken" } -ContentType "application/json" `
    -Body (@{ businessDate = $businessDate } | ConvertTo-Json)
Write-Host "Batch run $($run.batchRunId) accepted"

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds 3
    $details = Invoke-RestMethod -Uri "$baseUrl/api/v1/batch/eod/runs/$($run.batchRunId)" `
        -Headers @{ Authorization = "Bearer $adminToken" }
} while ($details.status -eq "RUNNING" -and (Get-Date) -lt $deadline)
Write-Host "Batch run $($run.batchRunId) ended $($details.status)"

# The notification is written by a Kafka consumer, so allow it a moment after the run ends.
$found = $false
$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Seconds 2
    $found = (Get-InterestNotices $customerToken) -gt 0
} while (-not $found -and (Get-Date) -lt $deadline)

if ($found) {
    Write-Host "PASS: $($vars.customerUsername) has a 'Monthly Interest Credited' notification" -ForegroundColor Green
    exit 0
}
Write-Host "FAIL: no 'Monthly Interest Credited' notification for $($vars.customerUsername)" -ForegroundColor Red
Write-Host "Check: docker logs --tail 80 batch-service  and  docker logs --tail 80 notification-service"
exit 1
