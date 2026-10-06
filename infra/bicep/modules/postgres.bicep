@description('Azure region for PostgreSQL')
param location string

@description('PostgreSQL Flexible Server name')
param serverName string

@description('Administrator login username')
param administratorLogin string = 'postgresadmin'

@description('Administrator login password')
@secure()
param administratorLoginPassword string

@description('Subnet Resource ID delegated to PostgreSQL Flexible Server')
param delegatedSubnetId string

@description('Private DNS Zone Resource ID for PostgreSQL')
param privateDnsZoneId string

@description('PostgreSQL Compute SKU (Standard_B2s = 2 vCPU, 4 GB RAM burstable)')
param skuName string = 'Standard_B2s'

@description('Storage size in GB')
param storageSizeGb int = 64

@description('Resource tags')
param tags object = {}

var databases = [
  'identity_db'
  'customer_db'
  'transaction_db'
  'notification_db'
  'audit_db'
  'batch_db'
]

resource postgresServer 'Microsoft.DBforPostgreSQL/flexibleServers@2023-12-01-preview' = {
  name: serverName
  location: location
  tags: tags
  sku: {
    name: skuName
    tier: 'Burstable'
  }
  properties: {
    version: '16'
    administratorLogin: administratorLogin
    administratorLoginPassword: administratorLoginPassword
    network: {
      delegatedSubnetResourceId: delegatedSubnetId
      privateDnsZoneArmResourceId: privateDnsZoneId
    }
    storage: {
      storageSizeGB: storageSizeGb
      autoGrow: 'Disabled'
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
  }
}

// Provision individual microservice databases
resource postgresDatabases 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-12-01-preview' = [for db in databases: {
  parent: postgresServer
  name: db
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}]

output serverId string = postgresServer.id
output serverFqdn string = postgresServer.properties.fullyQualifiedDomainName
output administratorLogin string = administratorLogin
