@description('Azure region for Redis')
param location string

@description('Redis Cache globally unique name')
param redisName string

@description('Resource tags')
param tags object = {}

resource redisCache 'Microsoft.Cache/redis@2023-08-01' = {
  name: redisName
  location: location
  tags: tags
  properties: {
    sku: {
      name: 'Basic'
      family: 'C'
      capacity: 1
    }
    enableNonSslPort: false
    minimumTlsVersion: '1.2'
    redisConfiguration: {
      'maxmemory-policy': 'volatile-lru'
    }
  }
}

output redisId string = redisCache.id
output redisHostName string = redisCache.properties.hostName
output redisSslPort int = redisCache.properties.sslPort
output redisPrimaryKey string = redisCache.listKeys().primaryKey
