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
