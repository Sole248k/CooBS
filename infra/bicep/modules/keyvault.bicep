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

@description('Optional Internal Service Secret (coobs-risk-internal-secret-2026)')
@secure()
param internalServiceSecret string = ''

@description('Optional Oracle Master DB admin password')
@secure()
param oraclePassword string = ''

@description('Optional Event Hubs primary connection string')
@secure()
param eventHubsConnectionString string = ''

@description('Optional Redis access key')
@secure()
param redisKey string = ''

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

// -----------------------------------------------------------------------------
// Production Secrets Seeding
// -----------------------------------------------------------------------------

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

resource secretInternal 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = if (!empty(internalServiceSecret)) {
  parent: keyVault
  name: 'internal-service-secret'
  properties: {
    value: internalServiceSecret
  }
}

resource secretOracle 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = if (!empty(oraclePassword)) {
  parent: keyVault
  name: 'oracle-password'
  properties: {
    value: oraclePassword
  }
}

resource secretEventHubs 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = if (!empty(eventHubsConnectionString)) {
  parent: keyVault
  name: 'eventhubs-connection-string'
  properties: {
    value: eventHubsConnectionString
  }
}

resource secretRedis 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = if (!empty(redisKey)) {
  parent: keyVault
  name: 'redis-key'
  properties: {
    value: redisKey
  }
}

output keyVaultId string = keyVault.id
output keyVaultName string = keyVault.name
output keyVaultUri string = keyVault.properties.vaultUri
