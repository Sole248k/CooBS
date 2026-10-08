targetScope = 'resourceGroup'

@description('Environment identifier (dev, demo, prod)')
@allowed([
  'dev'
  'demo'
  'prod'
])
param environmentName string = 'dev'

@description('Azure primary deployment region')
param location string = resourceGroup().location

@description('Base application prefix for naming resources')
param baseName string = 'coobs'

@description('PostgreSQL database administrator login username')
param postgresAdminUser string = 'postgresadmin'

@description('PostgreSQL database administrator login password')
@secure()
param postgresAdminPassword string

@description('Global JWT shared secret for HMAC-SHA256 bearer tokens')
@secure()
param jwtSecret string = ''

@description('Unique suffix for globally unique resources (Storage, ACR, KV, Redis)')
param uniqueSuffix string = uniqueString(resourceGroup().id)

var cleanUniqueSuffix = take(uniqueSuffix, 6)
var tags = {
  Project: 'CooBS'
  Capstone: 'Omnichannel Real-Time Remittance & Fraud Screening'
  Environment: environmentName
  CostCenter: 'Capstone-DevOps'
  ManagedBy: 'Bicep'
}

// =============================================================================
// 1. Networking Module (VNet, Subnets, NSGs, Private DNS)
// =============================================================================
module network './modules/network.bicep' = {
  name: 'deploy-network'
  params: {
    location: location
    vnetName: 'vnet-${baseName}-${environmentName}'
    tags: tags
  }
}

// =============================================================================
// 2. Monitoring & FinOps Governance Module
// =============================================================================
module monitoring './modules/monitoring.bicep' = {
  name: 'deploy-monitoring'
  params: {
    location: location
    workspaceName: 'law-${baseName}-${environmentName}'
    appInsightsName: 'appi-${baseName}-${environmentName}'
    dailyQuotaGb: 1
    retentionInDays: 30
    tags: tags
  }
}

// =============================================================================
// 3. Azure Container Registry (Basic SKU)
// =============================================================================
module acr './modules/acr.bicep' = {
  name: 'deploy-acr'
  params: {
    location: location
    acrName: '${baseName}acr${cleanUniqueSuffix}'
    sku: 'Basic'
    adminUserEnabled: true
    tags: tags
  }
}

// =============================================================================
// 4. Azure Key Vault (Secrets Management)
// =============================================================================
module keyvault './modules/keyvault.bicep' = {
  name: 'deploy-keyvault'
  params: {
    location: location
    keyVaultName: 'kv-${baseName}-${cleanUniqueSuffix}'
    jwtSecret: jwtSecret
    postgresPassword: postgresAdminPassword
    tags: tags
  }
}

// =============================================================================
// 5. Azure Event Hubs (Kafka Surface Backbone)
// =============================================================================
module eventhubs './modules/eventhubs.bicep' = {
  name: 'deploy-eventhubs'
  params: {
    location: location
    namespaceName: 'eh-${baseName}-events-${cleanUniqueSuffix}'
    capacity: 1
    maxThroughputUnits: 2
    tags: tags
  }
}

// =============================================================================
// 6. Azure Cache for Redis (Basic C1, 1GB RAM)
// =============================================================================
module redis './modules/redis.bicep' = {
  name: 'deploy-redis'
  params: {
    location: location
    redisName: 'redis-${baseName}-${cleanUniqueSuffix}'
    tags: tags
  }
}

// =============================================================================
// 7. Azure Database for PostgreSQL Flexible Server
// =============================================================================
module postgres './modules/postgres.bicep' = {
  name: 'deploy-postgres'
  params: {
    location: location
    serverName: 'psql-${baseName}-${cleanUniqueSuffix}'
    administratorLogin: postgresAdminUser
    administratorLoginPassword: postgresAdminPassword
    delegatedSubnetId: network.outputs.postgresSubnetId
    privateDnsZoneId: network.outputs.postgresPrivateDnsZoneId
    skuName: 'Standard_B2s'
    storageSizeGb: 64
    tags: tags
  }
}

// =============================================================================
// 8. Azure Kubernetes Service (AKS Cluster)
// =============================================================================
module aks './modules/aks.bicep' = {
  name: 'deploy-aks'
  params: {
    location: location
    clusterName: 'aks-${baseName}-${environmentName}'
    subnetId: network.outputs.aksSubnetId
    acrId: acr.outputs.acrId
    logAnalyticsWorkspaceId: monitoring.outputs.workspaceId
    vmSize: 'Standard_D4s_v5'
    nodeCount: 2
    minNodeCount: 2
    maxNodeCount: 3
    tags: tags
  }
}

// =============================================================================
// Root Outputs (for CI/CD pipeline and microservice configuration)
// =============================================================================
output vnetName string = network.outputs.vnetName
output acrLoginServer string = acr.outputs.loginServer
output aksClusterName string = aks.outputs.clusterName
output postgresFqdn string = postgres.outputs.serverFqdn
output redisHostName string = redis.outputs.redisHostName
output redisSslPort int = redis.outputs.redisSslPort
output kafkaBootstrapServer string = eventhubs.outputs.kafkaBootstrapServer
output keyVaultUri string = keyvault.outputs.keyVaultUri
output logAnalyticsWorkspaceId string = monitoring.outputs.workspaceId
