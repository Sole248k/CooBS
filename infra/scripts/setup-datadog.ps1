<#
.SYNOPSIS
    Sets up the Datadog side of the monitoring: two log pipelines, the dashboard and four monitors.
.DESCRIPTION
    The agents (deploy-cloud.ps1, deploy-onprem.ps1) send the data. This script makes Datadog
    read it properly and show it, through Datadog's own API, so nothing has to be clicked:

      - a log pipeline that reads the severity of the services' lines (they all arrive on
        stdout, so without it a real ERROR line is shown as information);
      - a log pipeline that does the same for PostgreSQL (it writes to stderr, so without it
        a routine checkpoint line is shown as an error);
      - the dashboard in infra/k8s-monitoring/datadog-dashboard.json;
      - four monitors: a host stopped reporting, a container restarted, server errors, and
        error lines logged by a service.

    It reads three secrets from Key Vault: datadog-api-key and datadog-site, which the agents
    already use, and datadog-app-key, an Application key made in Datadog under Organization
    Settings. No key is printed or written to a file.

    It can be run again: anything that already exists under the same name is left alone.
    It changes the Datadog account and nothing in Azure, so run it yourself. -DryRun prints
    what it would create and calls neither Azure nor Datadog.
.EXAMPLE
    .\infra\scripts\setup-datadog.ps1 -KeyVaultName kv-coobs-abc123
.EXAMPLE
    .\infra\scripts\setup-datadog.ps1 -DryRun
#>

[CmdletBinding()]
param(
    [string]$KeyVaultName,
    # Prints the names and sizes of what would be sent and stops.
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Get-VaultSecret([string]$name) {
    $value = az keyvault secret show --vault-name $KeyVaultName --name $name --query value --output tsv
    if ($LASTEXITCODE -ne 0 -or -not $value) { throw "Key Vault $KeyVaultName has no secret '$name'" }
    return ($value -join "").Trim()
}

# One call to Datadog. A refusal is shown with Datadog's own reason, which names the bad field.
function Invoke-Datadog([string]$method, [string]$path, [string]$body) {
    $request = @{ Method = $method; Uri = "$api$path"; Headers = $headers; ContentType = "application/json" }
    if ($body) { $request.Body = [System.Text.Encoding]::UTF8.GetBytes($body) }
    try {
        return Invoke-RestMethod @request
    } catch {
        $reason = $_.Exception.Message
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $reason = "$reason $($reader.ReadToEnd())"
        }
        throw "Datadog refused $method ${path}: $reason"
    }
}

# ---------------------------------------------------------------------------
# What is created. Names are how a second run recognises what is already there.
$pipelines = @(
    @{
        name = "CooBS services: severity"
        is_enabled = $true
        filter = @{ query = "(kube_namespace:coobs OR host:vm-coobs-onprem) -source:postgres" }
        processors = @(
            @{
                type = "grok-parser"; name = "Spring Boot line"; is_enabled = $true; source = "message"
                samples = @("2026-10-11T03:52:34.977Z  INFO 1 --- [transaction-service] [nio-8090-exec-5] c.g.f.t.c.HistoryInternalController      : Internal recent transactions lookup")
                # The second word of a service's line is its severity. Other lines are left as they are.
                grok = @{ support_rules = ""; match_rules = 'coobs_spring %{notSpace:log_time}\s+%{word:level}\s+%{data:rest}' }
            },
            @{ type = "status-remapper"; name = "Severity from the line"; is_enabled = $true; sources = @("level") }
        )
    },
    @{
        name = "CooBS PostgreSQL: severity"
        is_enabled = $true
        filter = @{ query = "source:postgres" }
        processors = @(
            @{
                type = "grok-parser"; name = "PostgreSQL line"; is_enabled = $true; source = "message"
                samples = @("2026-10-11 03:47:51.809 UTC [117] LOG:  checkpoint complete: wrote 9 buffers (0.1%)")
                grok = @{ support_rules = ""; match_rules = 'coobs_postgres %{notSpace:log_date}\s+%{notSpace:log_time}\s+%{notSpace:log_zone}\s+\[%{integer:pid}\]\s+%{word:level}:\s+%{data:rest}' }
            },
            @{ type = "status-remapper"; name = "Severity from the line"; is_enabled = $true; sources = @("level") }
        )
    }
)

$monitors = @(
    @{
        name = "CooBS: a host stopped reporting"
        type = "service check"
        query = '"datadog.agent.up".over("*").by("host").last(2).count_by_status()'
        message = "A Datadog agent has gone silent. The cluster node or the on-premises VM may be down."
        tags = @("project:coobs")
        options = @{ notify_no_data = $true; no_data_timeframe = 10; thresholds = @{ critical = 1; warning = 1; ok = 1 } }
    },
    @{
        name = "CooBS: a container restarted"
        type = "query alert"
        query = "change(max(last_5m),last_10m):max:kubernetes.containers.restarts{kube_namespace:coobs} by {kube_deployment} > 0"
        message = "A service on the cluster restarted. Read its log: kubectl logs deployment/<name> --namespace coobs --previous"
        tags = @("project:coobs")
        options = @{ thresholds = @{ critical = 0 } }
    },
    @{
        name = "CooBS: server errors"
        type = "query alert"
        query = "sum(last_5m):sum:coobs.http_server_requests_seconds.count{status:5*} by {application}.as_count() > 5"
        message = "A service answered more than five requests with a 5xx status in five minutes."
        tags = @("project:coobs")
        options = @{ thresholds = @{ critical = 5 } }
    },
    @{
        name = "CooBS: error lines logged by a service"
        type = "query alert"
        query = "sum(last_5m):sum:coobs.logback_events.count{level:error} by {application}.as_count() > 0"
        message = "A service logged at ERROR level. Search its logs in Datadog for the correlation ID."
        tags = @("project:coobs")
        options = @{ thresholds = @{ critical = 0 } }
    }
)

$dashboardJson = [System.IO.File]::ReadAllText((Join-Path $repo "infra\k8s-monitoring\datadog-dashboard.json"))
$dashboardTitle = ($dashboardJson | ConvertFrom-Json).title

if ($DryRun) {
    foreach ($pipeline in $pipelines) { Write-Host ("pipeline  {0}  ({1} bytes)" -f $pipeline.name, ($pipeline | ConvertTo-Json -Depth 10).Length) }
    Write-Host ("dashboard {0}  ({1} bytes, {2} widgets)" -f $dashboardTitle, $dashboardJson.Length, ($dashboardJson | ConvertFrom-Json).widgets.Count)
    foreach ($monitor in $monitors) { Write-Host ("monitor   {0}  [{1}]" -f $monitor.name, $monitor.query) }
    Write-Host "Dry run: nothing was sent to Azure or Datadog."
    return
}

if (-not $KeyVaultName) { throw "-KeyVaultName is required" }
$site = Get-VaultSecret "datadog-site"
if ($site -notmatch '^[a-z0-9.-]+$') { throw "The Key Vault secret datadog-site is not a host name" }
$api = "https://api.$site"
$headers = @{ "DD-API-KEY" = (Get-VaultSecret "datadog-api-key"); "DD-APPLICATION-KEY" = (Get-VaultSecret "datadog-app-key") }
Write-Host "Datadog at $site" -ForegroundColor Cyan

$existing = @(Invoke-Datadog "GET" "/api/v1/logs/config/pipelines") | ForEach-Object { $_.name }
foreach ($pipeline in $pipelines) {
    if ($existing -contains $pipeline.name) { Write-Host "   pipeline '$($pipeline.name)' exists, left alone"; continue }
    Invoke-Datadog "POST" "/api/v1/logs/config/pipelines" ($pipeline | ConvertTo-Json -Depth 10) | Out-Null
    Write-Host "   pipeline '$($pipeline.name)' created"
}

$boards = (Invoke-Datadog "GET" "/api/v1/dashboard").dashboards
$board = $boards | Where-Object { $_.title -eq $dashboardTitle } | Select-Object -First 1
if ($board) {
    Write-Host "   dashboard '$dashboardTitle' exists, left alone: https://app.$site$($board.url)"
} else {
    $board = Invoke-Datadog "POST" "/api/v1/dashboard" $dashboardJson
    Write-Host "   dashboard '$dashboardTitle' created: https://app.$site$($board.url)"
}

$existing = @(Invoke-Datadog "GET" "/api/v1/monitor?monitor_tags=project:coobs") | ForEach-Object { $_.name }
foreach ($monitor in $monitors) {
    if ($existing -contains $monitor.name) { Write-Host "   monitor '$($monitor.name)' exists, left alone"; continue }
    Invoke-Datadog "POST" "/api/v1/monitor" ($monitor | ConvertTo-Json -Depth 10) | Out-Null
    Write-Host "   monitor '$($monitor.name)' created"
}

Write-Host "Done. New log lines are read with the right severity from now on; lines already stored keep the old one." -ForegroundColor Green
