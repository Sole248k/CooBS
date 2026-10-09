<#
.SYNOPSIS
    Sends the on-premises zone to the VM and starts it.
.DESCRIPTION
    The VM has no public address, so nothing is copied to it over SSH. This script packs the
    Compose file, start.sh and the Oracle and audit-store init scripts into one archive (about
    30 KB), carries it inside an `az vm run-command` script, unpacks it into /opt/coobs on the
    VM and runs start.sh there. start.sh reads the secrets from Key Vault with the VM's own
    identity; no secret passes through this script.

    It changes the VM (it starts containers), so run it yourself. It needs the Azure CLI,
    signed in, and nothing else.

    The database init scripts run only on an empty volume. To rebuild from seed, remove the
    volumes on the VM first.
.EXAMPLE
    .\infra\scripts\deploy-onprem.ps1 -KeyVaultName kv-coobs-abc123 -ImageTag 20261010.1 -RedisHost redis-coobs-abc123.indonesiacentral.redis.azure.net
.EXAMPLE
    .\infra\scripts\deploy-onprem.ps1 -StatusOnly
#>

[CmdletBinding(DefaultParameterSetName = "Deploy")]
param(
    [Parameter(Mandatory, ParameterSetName = "Deploy")] [string]$KeyVaultName,
    [Parameter(Mandatory, ParameterSetName = "Deploy")] [string]$ImageTag,
    [Parameter(Mandatory, ParameterSetName = "Deploy")] [string]$RedisHost,
    [Parameter(ParameterSetName = "Deploy")] [string]$Registry = "coobs-asfagkfjadgsgfcf.azurecr.io",
    [Parameter(ParameterSetName = "Deploy")] [string]$KafkaBootstrap = "10.240.15.200:9094",
    [Parameter(Mandatory, ParameterSetName = "Status")] [switch]$StatusOnly,
    [string]$ResourceGroup = "CooBS-rsg",
    [string]$VmName = "vm-coobs-onprem",
    # Writes the script that would run on the VM to this file and stops, without calling Azure.
    [string]$SaveScriptTo
)

$ErrorActionPreference = "Stop"
$repo = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("coobs-onprem-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
New-Item -ItemType Directory -Path $work | Out-Null
$remote = Join-Path $work "remote.sh"
$noBom = New-Object System.Text.UTF8Encoding $false

if ($StatusOnly) {
    [System.IO.File]::WriteAllText($remote, "cd /opt/coobs && ./start.sh status`n", $noBom)
} else {
    foreach ($value in @($KeyVaultName, $ImageTag, $RedisHost, $Registry, $KafkaBootstrap)) {
        if ($value -notmatch '^[A-Za-z0-9._:\-]+$') { throw "Unexpected characters in '$value'" }
    }

    $stage = Join-Path $work "stage"
    New-Item -ItemType Directory -Path (Join-Path $stage "database") -Force | Out-Null
    Copy-Item (Join-Path $repo "infra\onprem\docker-compose.yml"), (Join-Path $repo "infra\onprem\start.sh") $stage
    Copy-Item (Join-Path $repo "database\oracle"), (Join-Path $repo "database\postgres") (Join-Path $stage "database") -Recurse

    $archive = Join-Path $work "bundle.tgz"
    tar -czf $archive -C $stage .
    if ($LASTEXITCODE -ne 0) { throw "tar failed" }
    $encoded = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($archive), "InsertLineBreaks") -replace "`r", ""

    # Line endings are fixed on the VM as well, in case this checkout has Windows ones.
    $script = @"
set -eu
install -d -m 0750 /opt/coobs
cd /opt/coobs
base64 -d > bundle.tgz <<'COOBS_BUNDLE'
$encoded
COOBS_BUNDLE
tar -xzf bundle.tgz
rm bundle.tgz
sed -i 's/\r`$//' start.sh docker-compose.yml
chmod +x start.sh
./start.sh '$KeyVaultName' '$Registry' '$ImageTag' '$RedisHost' '$KafkaBootstrap'
echo COOBS_STARTED
"@ -replace "`r", ""
    [System.IO.File]::WriteAllText($remote, $script + "`n", $noBom)
}

if ($SaveScriptTo) {
    Copy-Item $remote $SaveScriptTo -Force
    Write-Host "Saved the VM script to $SaveScriptTo. Nothing was sent to Azure."
    exit 0
}

Write-Host "Running on $VmName. The first start pulls Oracle and can take 10 minutes or more." -ForegroundColor Cyan
$output = az vm run-command invoke --resource-group $ResourceGroup --name $VmName --command-id RunShellScript `
    --scripts "@$remote" --query "value[0].message" --output tsv
$code = $LASTEXITCODE
$output
# run-command exits 0 even when the script on the VM failed, so the script's own last line is the proof.
if ($code -eq 0 -and -not $StatusOnly -and (($output -join "`n") -notmatch "COOBS_STARTED")) {
    Write-Host "start.sh did not finish on the VM. Its output is above." -ForegroundColor Red
    $code = 1
}
Get-ChildItem $work -Recurse -File | ForEach-Object { [System.IO.File]::Delete($_.FullName) }
exit $code
