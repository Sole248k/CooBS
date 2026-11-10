<#
.SYNOPSIS
    Checks the dead-letter path of notification-service end to end: park, list, replay, deliver.
.DESCRIPTION
    Scenario 8 of the service scope document: the notification consumer breaks, the record
    appears in the dead-letter list, an admin replays it, and the notification arrives.

    The break is real. The script stops the database that notification-service writes to,
    publishes one valid event, and waits for it to be parked on notification.events.DLT after
    the retries. It then starts the database again, replays the record through the admin
    endpoint, and looks for the notification in the customer's inbox.

    The event is a made-up "account credited" notice for the customer in the Postman
    environment file; no money moves. While the database is down (about a minute) every cloud
    service that uses it answers with errors, so run this on a test stack and not during E2E.
    Sign-in names and passwords are read from the Postman environment file.
#>

[CmdletBinding()]
param(
    [string]$EnvironmentFile = "postman/CooBS_Local.postman_environment.json",
    [string]$Topic = "ledger.mutation.completed.v1"
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

function Wait-Until([int]$seconds, [scriptblock]$condition) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        $result = & $condition
        if ($result) { return $result }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    return $null
}

# Tokens are taken while the identity database is still up.
$adminToken = Get-Token "/api/v1/auth/staff/login" $vars.staffUsername $vars.staffPassword
$customerToken = Get-Token "/api/v1/auth/customers/login" $vars.customerUsername $vars.customerPassword
$admin = @{ Authorization = "Bearer $adminToken" }
$me = Invoke-RestMethod -Uri "$baseUrl/api/v1/customers/me" -Headers @{ Authorization = "Bearer $customerToken" }
$customerId = $me.customerId

# The database notification-service is using: SQL Server with the azure-sql override, PostgreSQL without.
$serviceEnv = docker inspect notification-service --format "{{range .Config.Env}}{{println .}}{{end}}"
$database = if ($serviceEnv -match "sqlserver-cloud-db") { "sqlserver-cloud-db" } else { "postgres-cloud-db" }

$marker = "DLQ-TEST-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
$message = @{
    eventId   = "evt_$marker"
    eventType = "LEDGER_MUTATION_COMPLETED"
    timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    version   = "1.0"
    payload   = @{
        transactionId = 0; accountId = 1; accountNumber = $marker; transactionType = "CREDIT"
        amount = "1.00"; previousBalance = "0.00"; newBalance = "1.00"; referenceNo = $marker; customerId = $customerId
    }
} | ConvertTo-Json -Compress -Depth 5

$passed = $false
try {
    Write-Host "1. Stopping $database so that the consumer cannot store the notification" -ForegroundColor Cyan
    docker stop $database | Out-Null

    Write-Host "2. Publishing event evt_$marker to $Topic"
    # Sent from a file inside the broker container. Piping the text from PowerShell puts a
    # byte-order mark in front of it, which makes the record unreadable for a different reason.
    $file = Join-Path $env:TEMP "$marker.json"
    [System.IO.File]::WriteAllText($file, $message + "`n", (New-Object System.Text.UTF8Encoding $false))
    docker cp $file "kafka-broker:/tmp/$marker.json" | Out-Null
    Remove-Item $file
    docker exec kafka-broker sh -c "/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka-broker:29092 --topic $Topic < /tmp/$marker.json"

    Write-Host "3. Waiting for the record to be parked on the dead-letter topic after the retries"
    $record = Wait-Until 150 {
        (Invoke-RestMethod -Uri "$baseUrl/api/v1/notifications/admin/dlq" -Headers $admin) |
            Where-Object { $_.payload -match $marker } | Select-Object -Last 1
    }
    if (-not $record) { throw "the record never appeared in the dead-letter list" }
    if (-not $record.payload.StartsWith("{")) { throw "the parked payload is not clean JSON, so it was parked for the wrong reason" }
    Write-Host "   Parked as $($record.recordId) from $($record.originalTopic): $($record.errorMessage)"

    Write-Host "4. Starting $database again" -ForegroundColor Cyan
    docker start $database | Out-Null
    $healthy = Wait-Until 180 { (docker inspect $database --format "{{.State.Health.Status}}") -eq "healthy" }
    if (-not $healthy) { throw "$database did not become healthy" }
    Start-Sleep -Seconds 10

    Write-Host "5. Replaying $($record.recordId) as admin"
    $replay = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/notifications/admin/dlq/$($record.recordId)/replay" -Headers $admin
    if (-not $replay.replayed) { throw "the replay endpoint did not confirm the replay" }

    Write-Host "6. Looking for the notification in the customer's inbox"
    $passed = [bool](Wait-Until 90 {
        (Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/v1/notifications/my-notifications?size=50" `
            -Headers @{ Authorization = "Bearer $customerToken" }).Content -match $marker
    })
} catch {
    Write-Host "Stopped: $($_.Exception.Message)" -ForegroundColor Red
} finally {
    # Never leave the stack without its database.
    docker start $database | Out-Null
}

if ($passed) {
    Write-Host "PASS: the parked record was listed, replayed and delivered ($marker)" -ForegroundColor Green
    exit 0
}
Write-Host "FAIL: see the step above. Logs: docker logs --tail 120 notification-service" -ForegroundColor Red
exit 1
