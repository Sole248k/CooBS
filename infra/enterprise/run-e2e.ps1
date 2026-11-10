<#
.SYNOPSIS
    Runs the E2E suite against the enterprise deployment, with the address and the staff
    password read from Azure so that neither is typed.

.DESCRIPTION
    Reads the Front Door host name from the Bicep outputs when Front Door is on, otherwise the
    channels cluster's public address, and the staff password from the deployment's Key Vault.
    It changes nothing in Azure; the suite itself makes transfers in the deployed system.

.EXAMPLE
    .\infra\enterprise\run-e2e.ps1
#>

[CmdletBinding()]
param(
    [string]$ResourceGroup = "CooBS-ent-rsg",
    [string]$DeploymentName = "coobs-ent"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

$json = az deployment group show --resource-group $ResourceGroup --name $DeploymentName --query properties.outputs --output json
if ($LASTEXITCODE -ne 0) { throw "Could not read the outputs of $DeploymentName in $ResourceGroup. Are you signed in (az login)?" }
$outputs = ($json -join "`n") | ConvertFrom-Json
$vault = $outputs.keyVaultName.value
$frontDoor = if ($outputs.PSObject.Properties.Name -contains "frontDoorHostName") { $outputs.frontDoorHostName.value } else { "" }

if ($frontDoor) {
    $base = "https://$frontDoor"
} else {
    # The address of the Service coobs-public, which AKS names kubernetes-<id> in the cluster's own resource group.
    $cluster = $outputs.channelsClusterName.value
    $nodeGroup = ((az aks show --resource-group $ResourceGroup --name $cluster --query nodeResourceGroup --output tsv) -join "").Trim()
    $address = ((az network public-ip list --resource-group $nodeGroup --query "[?starts_with(name, 'kubernetes-')].ipAddress | [0]" --output tsv) -join "").Trim()
    if (-not $address) { throw "The channels cluster has no public address yet. Has deploy-enterprise.ps1 finished?" }
    $base = "http://$address"
}

$password = ((az keyvault secret show --vault-name $vault --name staff-password --query value --output tsv) -join "").Trim()
if (-not $password) { throw "Key Vault $vault has no staff-password. Run deploy-enterprise.ps1 first." }

# The suite's first step expects JSON from the gateway. Anything else means the address is wrong.
$health = Invoke-WebRequest -Uri "$base/actuator/health" -UseBasicParsing -TimeoutSec 20
if ($health.Content -notmatch '"status"\s*:\s*"UP"') { throw "$base/actuator/health did not answer UP. Nothing was run." }

Write-Host "Running the E2E suite against $base" -ForegroundColor Cyan
Push-Location $repo
try {
    npx --yes newman run postman/e2e/CooBS_End_To_End.postman_collection.json -e postman/CooBS_Local.postman_environment.json `
        --env-var "baseUrl=$base" --env-var "staffPassword=$password" --env-var "tellerPassword=$password" --delay-request 150 --reporters cli
} finally {
    Pop-Location
}
