using './main.bicep'

// No secret is written in this file. The three values below are read from the environment of
// the shell that runs the deployment; set them there first (see infra/README.md).

param environmentName = 'dev'
param location = 'indonesiacentral'
param baseName = 'coobs'
param acrName = 'CooBS'
param sqlAdminLogin = 'coobsadmin'

param sqlAdminPassword = readEnvironmentVariable('COOBS_SQL_ADMIN_PASSWORD', '')
param onPremAdminSshPublicKey = readEnvironmentVariable('COOBS_VM_SSH_PUBLIC_KEY', '')
param keyVaultAdminObjectId = readEnvironmentVariable('COOBS_KEYVAULT_ADMIN_OBJECT_ID', '')

// One fixed AKS node unless COOBS_AKS_MAX_NODES is 2 or 3, which switches the autoscaler on
// with that as its ceiling.
param aksMaxNodeCount = int(readEnvironmentVariable('COOBS_AKS_MAX_NODES', '1'))

// Front Door is off unless COOBS_FRONT_DOOR is 'true'. Switching it on also needs the public
// address that deploy-cloud.ps1 printed, in COOBS_PUBLIC_ADDRESS.
param deployFrontDoor = bool(readEnvironmentVariable('COOBS_FRONT_DOOR', 'false'))
param frontDoorOriginHost = readEnvironmentVariable('COOBS_PUBLIC_ADDRESS', '')
