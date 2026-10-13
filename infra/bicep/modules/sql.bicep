// Azure SQL: one logical server and the four cloud databases. The tables and seed come from
// database/azure-sql/ and are loaded after this, one script per database.

@description('Azure region for the logical server')
param location string

@description('Logical server name, globally unique')
param serverName string

@description('Administrator login')
param administratorLogin string

@description('Administrator password')
@secure()
param administratorLoginPassword string

@description('Subnet allowed to reach the server: the AKS subnet, which carries the Microsoft.Sql service endpoint')
param allowedSubnetId string

@description('Service objective of each database. S0 is 10 DTUs')
param skuName string = 'S0'

@description('Tier that goes with skuName')
param skuTier string = 'Standard'

@description('The five cloud databases (contracts/runtime/RUNTIME_SETTINGS.md section 3)')
param databaseNames array = [
  'identity_db'
  'customer_db'
  'transaction_db'
  'notification_db'
  'order_db'
]

@description('Resource tags')
param tags object = {}

// UTF-8, so VARCHAR columns keep every character the services write. See database/azure-sql/README.md.
var collation = 'Latin1_General_100_CI_AS_SC_UTF8'

resource sqlServer 'Microsoft.Sql/servers@2021-11-01' = {
  name: serverName
  location: location
  tags: tags
  properties: {
    administratorLogin: administratorLogin
    administratorLoginPassword: administratorLoginPassword
    version: '12.0'
    minimalTlsVersion: '1.2'
    // Has to stay Enabled for a virtual network rule to work. With no firewall rule, the
    // subnet below is the only thing that can connect.
    publicNetworkAccess: 'Enabled'
  }
}

resource allowAksSubnet 'Microsoft.Sql/servers/virtualNetworkRules@2021-11-01' = {
  parent: sqlServer
  name: 'allow-aks-subnet'
  properties: {
    virtualNetworkSubnetId: allowedSubnetId
    ignoreMissingVnetServiceEndpoint: false
  }
}

resource databases 'Microsoft.Sql/servers/databases@2021-11-01' = [
  for name in databaseNames: {
    parent: sqlServer
    name: name
    location: location
    tags: tags
    sku: {
      name: skuName
      tier: skuTier
    }
    properties: {
      collation: collation
      zoneRedundant: false
    }
  }
]

output serverName string = sqlServer.name
output serverFqdn string = sqlServer.properties.fullyQualifiedDomainName
