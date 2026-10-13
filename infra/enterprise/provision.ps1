<#
.SYNOPSIS
    Creates the enterprise-style deployment's Azure resources in a resource group of its own.

.DESCRIPTION
    Creates the resource group and its container registry if they are missing, then deploys
    infra/enterprise/main.bicep. Everything it creates costs money (README.md has the figures),
    so run it yourself. It does not touch the demo deployment in CooBS-rsg.

    It asks for the Azure SQL administrator password and passes it to the deployment through
    the environment of this process only; it is stored in the new Key Vault, nowhere else.

    -WhatIf asks Azure what the deployment would do and creates nothing except, if they are
    missing, the resource group and the registry, which the check itself needs.

.EXAMPLE
    .\infra\enterprise\provision.ps1 -RegistryName coobsentacr -WhatIf

.EXAMPLE
    .\infra\enterprise\provision.ps1 -RegistryName coobsentacr

.EXAMPLE
    .\infra\enterprise\provision.ps1 -RegistryName coobsentacr -FrontDoorAddress 20.1.2.3
#>

[CmdletBinding()]
param(
    # Globally unique, 5 to 50 lower-case letters and digits.
    [Parameter(Mandatory)] [ValidatePattern('^[a-z0-9]{5,50}$')] [string]$RegistryName,
    [string]$ResourceGroup = "CooBS-ent-rsg",
    [string]$Location = "indonesiacentral",
    [string]$DeploymentName = "coobs-ent",
    [string]$SshPublicKeyFile = (Join-Path $HOME ".ssh\id_ed25519.pub"),
    # Second pass: the public address deploy-enterprise.ps1 printed. Switches Front Door on.
    [string]$FrontDoorAddress,
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"

function Invoke-Native([string]$what, [scriptblock]$command) {
    $result = & $command
    if ($LASTEXITCODE -ne 0) { throw "$what failed (exit $LASTEXITCODE)" }
    return $result
}

if (-not (Test-Path $SshPublicKeyFile)) { throw "No SSH public key at $SshPublicKeyFile. Make one with ssh-keygen -t ed25519, or pass -SshPublicKeyFile." }

if ((az group exists --name $ResourceGroup) -ne "true") {
    Write-Host "Creating resource group $ResourceGroup in $Location" -ForegroundColor Cyan
    Invoke-Native "az group create" { az group create --name $ResourceGroup --location $Location --output none } | Out-Null
}
az acr show --name $RegistryName --resource-group $ResourceGroup --output none 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Creating container registry $RegistryName (Basic)" -ForegroundColor Cyan
    Invoke-Native "az acr create" { az acr create --resource-group $ResourceGroup --name $RegistryName --sku Basic --output none } | Out-Null
}

$secure = Read-Host "Azure SQL administrator password (upper case, lower case, a digit and a symbol)" -AsSecureString
$env:COOBS_SQL_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new("", $secure).Password
$env:COOBS_VM_SSH_PUBLIC_KEY = (Get-Content $SshPublicKeyFile -Raw).Trim()
$env:COOBS_KEYVAULT_ADMIN_OBJECT_ID = ((Invoke-Native "az ad signed-in-user show" { az ad signed-in-user show --query id --output tsv }) -join "").Trim()
$env:COOBS_ENT_ACR_NAME = $RegistryName
$env:COOBS_FRONT_DOOR = if ($FrontDoorAddress) { "true" } else { "false" }
$env:COOBS_PUBLIC_ADDRESS = $FrontDoorAddress
try {
    $parameters = Join-Path $PSScriptRoot "main.bicepparam"
    if ($WhatIf) {
        az deployment group what-if --resource-group $ResourceGroup --name $DeploymentName --parameters $parameters
    } else {
        # The cluster's logging add-on needs this provider; without it the cluster ends as Failed.
        Invoke-Native "az provider register" { az provider register --namespace Microsoft.OperationsManagement --wait } | Out-Null
        Write-Host "Deploying. Two clusters, Redis and the VM take 20 to 30 minutes." -ForegroundColor Cyan
        Invoke-Native "az deployment group create" {
            az deployment group create --resource-group $ResourceGroup --name $DeploymentName --parameters $parameters --output none
        } | Out-Null
        az deployment group show --resource-group $ResourceGroup --name $DeploymentName --query properties.outputs --output json
    }
} finally {
    # The password must not outlive this run in the shell's environment.
    Remove-Item Env:COOBS_SQL_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
