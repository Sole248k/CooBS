using './main.bicep'

// No secret is written in this file. The three values below are read from the environment of
// the shell that runs the deployment; set them there first (see infra/README.md).

param environmentName = 'demo'
param location = 'indonesiacentral'
param baseName = 'coobs'
// The registry created in this resource group before the deployment (README.md, step 1).
param acrName = readEnvironmentVariable('COOBS_ENT_ACR_NAME', '')
param sqlAdminLogin = 'coobsadmin'

param sqlAdminPassword = readEnvironmentVariable('COOBS_SQL_ADMIN_PASSWORD', '')
param onPremAdminSshPublicKey = readEnvironmentVariable('COOBS_VM_SSH_PUBLIC_KEY', '')
param keyVaultAdminObjectId = readEnvironmentVariable('COOBS_KEYVAULT_ADMIN_OBJECT_ID', '')

// Front Door is off unless COOBS_FRONT_DOOR is 'true'. Switching it on also needs the public
// address that deploy-cloud.ps1 printed, in COOBS_PUBLIC_ADDRESS.
param deployFrontDoor = bool(readEnvironmentVariable('COOBS_FRONT_DOOR', 'false'))
param frontDoorOriginHost = readEnvironmentVariable('COOBS_PUBLIC_ADDRESS', '')
