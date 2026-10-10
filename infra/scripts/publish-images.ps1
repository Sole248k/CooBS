<#
.SYNOPSIS
    Builds the service images with the Docker on this machine and pushes them to the registry.
.DESCRIPTION
    The route for publishing images while the pipeline is switched off. It builds each service
    from its own folder (backend/<service>, or frontend/ for the staff console: the same context
    and Dockerfile that Compose and the pipeline use) and pushes it as <registry>/coobs/<service>:<tag>, which is the name the cluster
    manifests (infra/k8s/kustomization.yaml) and the on-premises Compose file expect.

    The default list is everything that is deployed: nine services and the staff console on the
    cluster, and four services on the VM.

    Run it on a machine with Docker, from a clean checkout of the commit to deploy. Sign in to
    the registry first; this script does not handle credentials:

        az acr login --name CooBS

    It pushes to the registry, so run it yourself. -DryRun prints what it would do and calls
    nothing.

    Building inside Azure instead (az acr build) is not used: this subscription is a free
    trial, and registry build tasks are not expected to be allowed on one. Not tried.
.EXAMPLE
    .\infra\scripts\publish-images.ps1 -ImageTag 20261010.1
.EXAMPLE
    .\infra\scripts\publish-images.ps1 -ImageTag 20261010.1 -DryRun
.EXAMPLE
    .\infra\scripts\publish-images.ps1 -ImageTag 20261010.2 -Services transaction-service, orchestration-service
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)] [ValidatePattern('^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$')] [string]$ImageTag,
    [string]$Registry = "coobs-asfagkfjadgsgfcf.azurecr.io",
    [string[]]$Services = @(
        # the cluster
        "frontend", "api-gateway", "mobile-bff", "staff-bff", "auth-service", "customer-service", "transaction-service",
        "orchestration-service", "risk-engine", "notification-service",
        # the on-premises VM
        "ledger-service", "account-service", "audit-service", "batch-service"
    ),
    # Prints the commands and stops; nothing is built or pushed.
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repo = Resolve-Path (Join-Path $PSScriptRoot "..\..")

# The staff console is the one image that is not built from backend/.
function Get-BuildContext([string]$service) {
    if ($service -eq "frontend") { return Join-Path $repo "frontend" }
    return Join-Path $repo "backend\$service"
}

foreach ($service in $Services) {
    if (-not (Test-Path (Join-Path (Get-BuildContext $service) "Dockerfile"))) {
        throw "$(Get-BuildContext $service) has no Dockerfile. Check the service name."
    }
}

# What is being published: the commit, and whether the checkout has changes that are not in it.
$commit = (git -C $repo rev-parse --short HEAD)
$branch = (git -C $repo rev-parse --abbrev-ref HEAD)
$uncommitted = @(git -C $repo status --porcelain -- backend frontend).Count
Write-Host "Publishing $($Services.Count) images from commit $commit ($branch) as tag $ImageTag to $Registry" -ForegroundColor Cyan
if ($uncommitted) {
    Write-Host "WARNING: backend/ or frontend/ has $uncommitted uncommitted change(s). The images will contain them, and the commit above will not." -ForegroundColor Yellow
}

$published = @()
foreach ($service in $Services) {
    $image = "$Registry/coobs/${service}:$ImageTag"
    $context = Get-BuildContext $service
    # linux/amd64 is what the cluster node and the VM run, whatever this machine is.
    $build = @("build", "--platform", "linux/amd64", "--label", "org.opencontainers.image.revision=$commit",
        "--tag", $image, $context)
    $push = @("push", $image)

    Write-Host "`n== $service ==" -ForegroundColor Cyan
    if ($DryRun) {
        Write-Host "docker $($build -join ' ')"
        Write-Host "docker $($push -join ' ')"
        continue
    }
    docker @build
    if ($LASTEXITCODE -ne 0) { throw "The build of $service failed. Nothing after it was pushed." }
    docker @push
    if ($LASTEXITCODE -ne 0) {
        throw "The push of $service failed. If the message is about authentication, run: az acr login --name CooBS"
    }
    $published += $image
}

Write-Host ""
if ($DryRun) {
    Write-Host "Dry run: nothing was built or pushed." -ForegroundColor Yellow
    exit 0
}
Write-Host "Published $($published.Count) images:" -ForegroundColor Green
$published | ForEach-Object { Write-Host "  $_" }
Write-Host "`nNext, from a machine signed in to Azure:"
Write-Host "  .\infra\scripts\deploy-cloud.ps1 -ImageTag $ImageTag"
Write-Host "  .\infra\scripts\deploy-onprem.ps1 -KeyVaultName <vault> -ImageTag $ImageTag -RedisHost <redis host name>"
