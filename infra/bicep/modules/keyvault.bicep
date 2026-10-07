@description('Azure region for Key Vault')
param location string

@description('Key Vault globally unique name (3-24 alphanumerics and dashes)')
param keyVaultName string

@description('Resource tags')
param tags object = {}

@description('Optional initial JWT Secret value')
@secure()
param jwtSecret string = ''

@description('Optional PostgreSQL master password')
@secure()
param postgresPassword string = ''

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
    enabledForDeployment: true
    enabledForTemplateDeployment: true
    enabledForDiskEncryption: false
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
  }
}

// Optional secret seeding if provided
resource secretJwt 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = if (!empty(jwtSecret)) {
  parent: keyVault
  name: 'jwt-secret'
  properties: {
    value: jwtSecret
  }
}

resource secretPostgres 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = if (!empty(postgresPassword)) {
  parent: keyVault
  name: 'postgres-password'
  properties: {
    value: postgresPassword
  }
}

output keyVaultId string = keyVault.id
output keyVaultName string = keyVault.name
output keyVaultUri string = keyVault.properties.vaultUri
