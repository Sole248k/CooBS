// Key Vault holds every secret of the deployment. Two are written here because Azure produces
// them (the SQL administrator password given at deploy time, and the Redis key, written by
// redis.bicep). The application secrets (JWT, internal service, inter-bank, on-premises
// database passwords) are generated and stored after the deployment and never pass through a
// template or a file in this repository.

@description('Azure region for Key Vault')
param location string

@description('Key Vault globally unique name (3-24 alphanumerics and dashes)')
param keyVaultName string

@description('Object ID of the person who seeds and reads the secrets. Owner of the subscription is not enough: with RBAC authorisation the data plane needs its own role')
param keyVaultAdminObjectId string

@description('Azure SQL administrator password, stored as cloud-db-password')
@secure()
param cloudDbPassword string

@description('Resource tags')
param tags object = {}

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: keyVaultName
  location: location
  tags: tags
  properties: {
    sku: {
      family: 'A'
      name: 'standard'
    }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
    enabledForDeployment: false
    enabledForTemplateDeployment: true
    enabledForDiskEncryption: false
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
  }
}

// Key Vault Secrets Officer: b86a8fe4-44ce-4948-aee5-eccb2c155cd7
var secretsOfficerRoleDefinitionId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', 'b86a8fe4-44ce-4948-aee5-eccb2c155cd7')

resource secretsOfficerRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: keyVault
  name: guid(keyVault.id, keyVaultAdminObjectId, secretsOfficerRoleDefinitionId)
  properties: {
    roleDefinitionId: secretsOfficerRoleDefinitionId
    principalId: keyVaultAdminObjectId
    principalType: 'User'
  }
}

resource secretCloudDb 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'cloud-db-password'
  properties: {
    value: cloudDbPassword
  }
}

output keyVaultId string = keyVault.id
output keyVaultName string = keyVault.name
output keyVaultUri string = keyVault.properties.vaultUri
