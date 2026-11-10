<#
.SYNOPSIS
    Generates TLS/HTTPS certificates and a PKCS12 keystore for API Gateway and Kubernetes Ingress.
.DESCRIPTION
    Task: FIN-807 - Production Cloud Hardening: Key Vault, TLS & Grafana Dashboards
    Creates:
      1. Java PKCS12 keystore (keystore.p12) for Spring Boot API Gateway
      2. X.509 public certificate (tls.crt / coobs-cert.crt)
      3. Azure Key Vault / Kubernetes import instructions
.PARAMETER OutputDir
    Directory to save the generated certificates. Default: infra/certs
.PARAMETER CopyToGateway
    If switch is present, also copies the keystore to backend/api-gateway/src/main/resources/keystore/
.PARAMETER KeystorePassword
    Password for the keystore. Default: changeit
.PARAMETER Alias
    Key alias name. Default: coobs-gateway
.PARAMETER ValidityDays
    Certificate validity in days. Default: 365
#>

[CmdletBinding()]
param(
    [string]$OutputDir = "infra/certs",
    [switch]$CopyToGateway,
    [string]$KeystorePassword = "changeit",
    [string]$Alias = "coobs-gateway",
    [int]$ValidityDays = 365
)

$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "    CooBS Banking Platform - TLS Certificate & Keystore Generator" -ForegroundColor Cyan
Write-Host "    Task FIN-807: Production Cloud Hardening (TLS / Key Vault)   " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# Resolve output path relative to workspace root
$WorkspaceRoot = (Resolve-Path "$PSScriptRoot/..").Path
$TargetDir = Join-Path $WorkspaceRoot $OutputDir

if (-not (Test-Path $TargetDir)) {
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
    Write-Host "[+] Created certificate directory: $TargetDir" -ForegroundColor Green
}

# 1. Locate keytool executable
$KeytoolCmd = Get-Command "keytool" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $KeytoolCmd) {
    $Candidates = @(
        "$env:JAVA_HOME\bin\keytool.exe",
        "C:\Program Files\Java\jdk-21\bin\keytool.exe",
        "C:\Program Files\Java\jdk-17.0.19\bin\keytool.exe",
        "C:\Program Files\Java\latest\bin\keytool.exe"
    )
    foreach ($cand in $Candidates) {
        if (Test-Path $cand) {
            $KeytoolCmd = $cand
            break
        }
    }
}

if (-not $KeytoolCmd) {
    Write-Error "keytool executable not found. Ensure Java JDK 17+ is installed and JAVA_HOME is configured."
    exit 1
}

Write-Host "[*] Using keytool: $KeytoolCmd" -ForegroundColor Gray

$KeystorePath = Join-Path $TargetDir "keystore.p12"
$CertPath = Join-Path $TargetDir "tls.crt"

# If keystore already exists, backup or remove
if (Test-Path $KeystorePath) {
    Remove-Item $KeystorePath -Force
    Write-Host "[*] Removed existing keystore at: $KeystorePath" -ForegroundColor Yellow
}

# 2. Generate PKCS12 key pair and certificate
$Dname = "CN=coobs-gateway.southeastasia.cloudapp.azure.com, OU=Platform Engineering, O=CooBS Retail Banking, L=Singapore, ST=SG, C=SG"
$San = "SAN=dns:localhost,dns:api.coobs.internal,dns:coobs-gateway,dns:coobs-gateway.southeastasia.cloudapp.azure.com,ip:127.0.0.1"

Write-Host "[1/3] Generating 2048-bit RSA PKCS12 keystore..." -ForegroundColor Cyan
& $KeytoolCmd -genkeypair `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity $ValidityDays `
    -keystore $KeystorePath `
    -storetype PKCS12 `
    -storepass $KeystorePassword `
    -dname $Dname `
    -ext $San

if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to generate PKCS12 keystore."
    exit 1
}
Write-Host "  [PASS] Keystore generated: $KeystorePath" -ForegroundColor Green

# 3. Export Public X.509 Certificate
Write-Host "[2/3] Exporting X.509 RFC-1421 / PEM certificate..." -ForegroundColor Cyan
& $KeytoolCmd -exportcert `
    -alias $Alias `
    -keystore $KeystorePath `
    -storepass $KeystorePassword `
    -rfc `
    -file $CertPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "  [PASS] Public certificate exported: $CertPath" -ForegroundColor Green
}

# 4. Optional Copy to api-gateway resources
if ($CopyToGateway) {
    $GatewayKeystoreDir = Join-Path $WorkspaceRoot "backend/api-gateway/src/main/resources/keystore"
    if (-not (Test-Path $GatewayKeystoreDir)) {
        New-Item -ItemType Directory -Path $GatewayKeystoreDir -Force | Out-Null
    }
    Copy-Item -Path $KeystorePath -Destination (Join-Path $GatewayKeystoreDir "keystore.p12") -Force
    Write-Host "[3/3] Copied keystore to API Gateway resources: $GatewayKeystoreDir/keystore.p12" -ForegroundColor Green
} else {
    Write-Host "[3/3] Keystore ready for deployment or manual activation." -ForegroundColor Cyan
}

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Green
Write-Host "               TLS PROVISIONING COMPLETE (FIN-807)               " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
Write-Host "Generated Artifacts:"
Write-Host "  - Keystore:    $KeystorePath" -ForegroundColor White
Write-Host "  - Public Cert: $CertPath" -ForegroundColor White
Write-Host ""
Write-Host "How to Enable TLS in Local API Gateway:" -ForegroundColor Yellow
Write-Host "  `$env:SSL_ENABLED = 'true'" -ForegroundColor Gray
Write-Host "  `$env:SERVER_PORT = '8443'" -ForegroundColor Gray
Write-Host "  `$env:SSL_KEY_STORE = 'classpath:keystore/keystore.p12'" -ForegroundColor Gray
Write-Host ""
Write-Host "How to Import into Azure Key Vault (Cloud Production):" -ForegroundColor Yellow
Write-Host "  az keyvault certificate import --vault-name <kv-name> --name coobs-tls-cert --file $KeystorePath --password $KeystorePassword" -ForegroundColor Gray
Write-Host ""
Write-Host "How to Deploy to Kubernetes Ingress:" -ForegroundColor Yellow
Write-Host "  kubectl create secret tls coobs-tls-secret --cert=$CertPath --key=<tls.key>" -ForegroundColor Gray
Write-Host "  kubectl apply -f infra/k8s/ingress-tls.yaml" -ForegroundColor Gray
Write-Host "=================================================================" -ForegroundColor Green
