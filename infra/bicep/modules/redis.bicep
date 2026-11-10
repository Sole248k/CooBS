// Azure Managed Redis, the successor of Azure Cache for Redis (which can no longer be created).
// The cluster and the on-premises VM both reach it through one private endpoint; the private
// DNS zone is linked to both networks so the public host name resolves to that address.

@description('Azure region for Redis')
param location string

@description('Redis name, globally unique')
param redisName string

@description('Smallest Balanced size')
param skuName string = 'Balanced_B0'

@description('Subnet for the private endpoint')
param privateEndpointSubnetId string

@description('Networks whose services use Redis: the cloud VNet and the on-premises VNet')
param linkedVnetIds array

@description('Key Vault that receives the access key as the secret redis-key')
param keyVaultName string

@description('Resource tags')
param tags object = {}

var privateDnsZoneName = 'privatelink.redis.azure.net'

resource redis 'Microsoft.Cache/redisEnterprise@2025-04-01' = {
  name: redisName
  location: location
  tags: tags
  sku: {
    name: skuName
  }
  properties: {
    minimumTlsVersion: '1.2'
    // One node. Enough for a demo, and half the price of the replicated default.
    highAvailability: 'Disabled'
  }
}

// The services use a plain single-endpoint client with a password: TLS on port 10000, the
// Enterprise policy (one endpoint in front of the shards) and access keys switched on.
resource database 'Microsoft.Cache/redisEnterprise/databases@2025-04-01' = {
  parent: redis
  name: 'default'
  properties: {
    clientProtocol: 'Encrypted'
    port: 10000
    clusteringPolicy: 'EnterpriseCluster'
    evictionPolicy: 'VolatileLRU'
    accessKeysAuthentication: 'Enabled'
  }
}

resource privateDnsZone 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: privateDnsZoneName
  location: 'global'
  tags: tags
}

resource dnsLinks 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = [
  for vnetId in linkedVnetIds: {
    parent: privateDnsZone
    name: 'link-${last(split(vnetId, '/'))}'
    location: 'global'
    properties: {
      registrationEnabled: false
      virtualNetwork: {
        id: vnetId
      }
    }
  }
]

resource privateEndpoint 'Microsoft.Network/privateEndpoints@2023-09-01' = {
  name: 'pe-${redisName}'
  location: location
  tags: tags
  properties: {
    subnet: {
      id: privateEndpointSubnetId
    }
    privateLinkServiceConnections: [
      {
        name: 'redis'
        properties: {
          privateLinkServiceId: redis.id
          groupIds: [
            'redisEnterprise'
          ]
        }
      }
    ]
  }
  dependsOn: [
    database
  ]
}

resource dnsZoneGroup 'Microsoft.Network/privateEndpoints/privateDnsZoneGroups@2023-09-01' = {
  parent: privateEndpoint
  name: 'default'
  properties: {
    privateDnsZoneConfigs: [
      {
        name: 'redis'
        properties: {
          privateDnsZoneId: privateDnsZone.id
        }
      }
    ]
  }
}

// The key goes straight into the vault, so it never appears in a deployment output.
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: keyVaultName
}

resource secretRedisKey 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'redis-key'
  properties: {
    value: database.listKeys().primaryKey
  }
}

output redisId string = redis.id
output redisHostName string = redis.properties.hostName
output redisPort int = 10000
