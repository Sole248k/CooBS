// CooBS enterprise-style deployment: the same system as infra/bicep, split over two AKS clusters,
// in a resource group of its own. The demo deployment (infra/bicep, CooBS-rsg) is not touched.
//
//   Channels cluster   the staff console, api-gateway, mobile-bff, staff-bff; the only public entry
//   Services cluster   auth, customer, transaction, orchestration, risk-engine, notification, order, Kafka
//   On-premises VNet   one VM running Docker Compose (ledger, account, audit, batch, Oracle, audit store)
//   Edge               Front Door in front of the channels cluster, switched on in a second pass
//
// Both clusters take their addresses from the one AKS subnet, so the SQL rule and the on-premises
// firewall, which admit that subnet, cover both. The modules are the ones infra/bicep uses.
// A container registry must already exist in this resource group (infra/enterprise/README.md).
targetScope = 'resourceGroup'

@description('Environment identifier (dev, demo, prod)')
@allowed([
  'dev'
  'demo'
  'prod'
])
param environmentName string = 'dev'

@description('Azure region. Indonesia Central: Southeast Asia offers the trial subscription no usable VM size')
param location string = resourceGroup().location

@description('Base application prefix for naming resources')
param baseName string = 'coobs'

@description('Name of the container registry that already exists in this resource group')
param acrName string = 'CooBS'

@description('Azure SQL administrator login')
param sqlAdminLogin string = 'coobsadmin'

@description('Azure SQL administrator password. Given at deploy time; never written to a file in this repository')
@secure()
param sqlAdminPassword string

@description('SSH public key for the on-premises VM administrator')
param onPremAdminSshPublicKey string

@description('Object ID of the person who seeds and reads Key Vault secrets (az ad signed-in-user show --query id)')
param keyVaultAdminObjectId string

@description('Create Azure Managed Redis. Set false to run Redis in the cluster instead (infra/k8s/optional/redis-in-cluster.yaml)')
param deployManagedRedis bool = true

@description('Send container logs to Log Analytics')
param enableContainerInsights bool = true

@description('Put Front Door (HTTPS) in front of the public address. Leave false for the first deployment: the address does not exist yet')
param deployFrontDoor bool = false

@description('Public address of the coobs-public load balancer, as printed by infra/scripts/deploy-cloud.ps1. Needed only when deployFrontDoor is true')
param frontDoorOriginHost string = ''

// The demo deployment's sizes (E2as_v5, B2as_v2) were granted under the free trial. On
// pay-as-you-go those two families have a quota of 0 here; these two have 10 vCPUs each
// (read on 13 October 2026), with the same vCPUs and memory.
@description('AKS node size for both clusters: 2 vCPU, 16 GB')
param aksNodeSize string = 'Standard_E2as_v4'

@description('On-premises VM size: 2 vCPU, 8 GB')
param onPremVmSize string = 'Standard_D2as_v4'

@description('Unique suffix for globally unique resources (SQL, Key Vault, Redis)')
param uniqueSuffix string = uniqueString(resourceGroup().id)

var cleanUniqueSuffix = take(uniqueSuffix, 6)
var tags = {
  Project: 'CooBS'
  Capstone: 'Omnichannel Real-Time Remittance & Fraud Screening'
  Environment: environmentName
  CostCenter: 'Capstone-DevOps'
  ManagedBy: 'Bicep'
}

// Fixed private addresses that infra/k8s/config.yaml also carries. Change both together.
var onPremVmPrivateIp = '10.250.0.4'
var kafkaInternalLoadBalancerIp = '10.240.15.200'

// =============================================================================
// 1. Networking: cloud VNet, on-premises VNet, peering, firewall NSG, NAT gateway
// =============================================================================
module network '../bicep/modules/network.bicep' = {
  name: 'deploy-network'
  params: {
    location: location
    cloudVnetName: 'vnet-${baseName}-cloud-${environmentName}'
    onPremVnetName: 'vnet-${baseName}-onprem-${environmentName}'
    tags: tags
  }
}

// =============================================================================
// 2. Monitoring
// =============================================================================
module monitoring '../bicep/modules/monitoring.bicep' = {
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
// 3. Key Vault
// =============================================================================
module keyvault '../bicep/modules/keyvault.bicep' = {
  name: 'deploy-keyvault'
  params: {
    location: location
    keyVaultName: 'kv-${baseName}-${cleanUniqueSuffix}'
    keyVaultAdminObjectId: keyVaultAdminObjectId
    cloudDbPassword: sqlAdminPassword
    tags: tags
  }
}

// =============================================================================
// 4. Azure SQL: identity_db, customer_db, transaction_db, notification_db
// =============================================================================
module sql '../bicep/modules/sql.bicep' = {
  name: 'deploy-sql'
  params: {
    location: location
    serverName: 'sql-${baseName}-${cleanUniqueSuffix}'
    administratorLogin: sqlAdminLogin
    administratorLoginPassword: sqlAdminPassword
    allowedSubnetId: network.outputs.aksSubnetId
    tags: tags
  }
}

// =============================================================================
// 5. Azure Managed Redis (Balanced B0), reached by both networks through a private endpoint
// =============================================================================
module redis '../bicep/modules/redis.bicep' = if (deployManagedRedis) {
  name: 'deploy-redis'
  params: {
    location: location
    redisName: 'redis-${baseName}-${cleanUniqueSuffix}'
    privateEndpointSubnetId: network.outputs.privateEndpointSubnetId
    linkedVnetIds: [
      network.outputs.cloudVnetId
      network.outputs.onPremVnetId
    ]
    keyVaultName: keyvault.outputs.keyVaultName
    tags: tags
  }
}

// =============================================================================
// 6. AKS: one node, Free tier
// =============================================================================
module aksChannels '../bicep/modules/aks.bicep' = {
  name: 'deploy-aks-channels'
  params: {
    location: location
    clusterName: 'aks-${baseName}-channels'
    subnetId: network.outputs.aksSubnetId
    acrName: acrName
    logAnalyticsWorkspaceId: monitoring.outputs.workspaceId
    enableContainerInsights: enableContainerInsights
    vmSize: aksNodeSize
    tags: tags
  }
}

module aksServices '../bicep/modules/aks.bicep' = {
  name: 'deploy-aks-services'
  params: {
    location: location
    clusterName: 'aks-${baseName}-services'
    subnetId: network.outputs.aksSubnetId
    acrName: acrName
    logAnalyticsWorkspaceId: monitoring.outputs.workspaceId
    enableContainerInsights: enableContainerInsights
    vmSize: aksNodeSize
    tags: tags
  }
  // One at a time: both give their identity a role on the same virtual network.
  dependsOn: [
    aksChannels
  ]
}

// =============================================================================
// 7. On-premises VM (the simulated datacenter)
// =============================================================================
module onPremVm '../bicep/modules/onprem-vm.bicep' = {
  name: 'deploy-onprem-vm'
  params: {
    location: location
    vmName: 'vm-${baseName}-onprem'
    subnetId: network.outputs.onPremSubnetId
    privateIpAddress: onPremVmPrivateIp
    vmSize: onPremVmSize
    adminSshPublicKey: onPremAdminSshPublicKey
    acrName: acrName
    keyVaultName: keyvault.outputs.keyVaultName
    tags: tags
  }
}

// =============================================================================
// 8. Front Door: HTTPS in front of the one public address. Off until E2E has passed.
// =============================================================================
module frontDoor '../bicep/modules/frontdoor.bicep' = if (deployFrontDoor) {
  name: 'deploy-frontdoor'
  params: {
    profileName: 'afd-${baseName}-${environmentName}'
    endpointName: '${baseName}-${cleanUniqueSuffix}'
    originHost: frontDoorOriginHost
    tags: tags
  }
}

// =============================================================================
// Outputs: the values infra/k8s/config.yaml and the on-premises Compose file need
// =============================================================================
output channelsClusterName string = aksChannels.outputs.clusterName
output servicesClusterName string = aksServices.outputs.clusterName
output keyVaultName string = keyvault.outputs.keyVaultName
output sqlServerFqdn string = sql.outputs.serverFqdn
output sqlAdminLogin string = sqlAdminLogin
#disable-next-line BCP318
output redisHostName string = deployManagedRedis ? redis.outputs.redisHostName : ''
output redisPort int = 10000
output onPremVmName string = onPremVm.outputs.vmName
output onPremVmPrivateIp string = onPremVmPrivateIp
output kafkaInternalLoadBalancerIp string = kafkaInternalLoadBalancerIp
output onPremOutboundPublicIp string = network.outputs.natPublicIpAddress
output logAnalyticsWorkspaceId string = monitoring.outputs.workspaceId
// Empty while Front Door is off. deploy-cloud.ps1 reads it to allow the HTTPS origin and to
// close the load balancer to everything but Front Door.
#disable-next-line BCP318
output frontDoorHostName string = deployFrontDoor ? frontDoor.outputs.hostName : ''
