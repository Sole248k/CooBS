@description('Azure region for AKS')
param location string

@description('AKS Cluster name')
param clusterName string

@description('Subnet Resource ID for AKS cluster')
param subnetId string

@description('Log Analytics Workspace Resource ID for Container Insights')
param logAnalyticsWorkspaceId string

@description('Existing container registry the cluster pulls from (AcrPull)')
param acrName string

@description('2 vCPUs and 16 GB. Eight JVMs and Kafka share one node, so memory is the limit, not CPU')
param vmSize string = 'Standard_E2as_v5'

@description('Nodes the pool starts with. One runs the whole cloud zone; its CPU reservations are 95 percent taken')
param nodeCount int = 1

@description('Most nodes the autoscaler may add. Equal to nodeCount (the default) leaves autoscaling off. 2 lets a rollout that finds no room add one node, and caps the cost at two')
@minValue(1)
@maxValue(3)
param maxNodeCount int = 1

@description('The only zone in which the trial subscription may create this size')
param availabilityZones array = [
  '3'
]

@description('The Free tier takes 1.34 to 1.36 in Indonesia Central; 1.31 to 1.33 are long-term-support only, which needs the Premium tier')
param kubernetesVersion string = '1.35'

@description('Send container logs to Log Analytics. The agent takes a few hundred MB of the node')
param enableContainerInsights bool = true

@description('Resource tags')
param tags object = {}

// Managed Identity for AKS Control Plane
resource aksIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${clusterName}-identity'
  location: location
  tags: tags
}

// The control plane identity places the Kafka internal load balancer in the node subnet,
// which needs Network Contributor on the virtual network.
var networkContributorRoleDefinitionId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4d97b98b-1d4f-4787-a291-c67834d212e7')
var vnetName = split(subnetId, '/')[8]

resource clusterVnet 'Microsoft.Network/virtualNetworks@2024-05-01' existing = {
  name: vnetName
}

resource networkContributorRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: clusterVnet
  name: guid(aksIdentity.id, clusterVnet.id, networkContributorRoleDefinitionId)
  properties: {
    roleDefinitionId: networkContributorRoleDefinitionId
    principalId: aksIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

// AKS Cluster
resource aksCluster 'Microsoft.ContainerService/managedClusters@2024-02-01' = {
  name: clusterName
  location: location
  tags: tags
  sku: {
    name: 'Base'
    tier: 'Free'
  }
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${aksIdentity.id}': {}
    }
  }
  properties: {
    kubernetesVersion: kubernetesVersion
    dnsPrefix: clusterName
    enableRBAC: true
    agentPoolProfiles: [
      {
        name: 'agentpool'
        count: nodeCount
        vmSize: vmSize
        osType: 'Linux'
        mode: 'System'
        vnetSubnetID: subnetId
        availabilityZones: availabilityZones
        // Off unless maxNodeCount is raised. The upper bound is the cost ceiling.
        enableAutoScaling: maxNodeCount > nodeCount
        minCount: maxNodeCount > nodeCount ? nodeCount : null
        maxCount: maxNodeCount > nodeCount ? maxNodeCount : null
        // Every pod takes an address from the subnet; the default of 30 leaves no room for a rollout.
        maxPods: 60
        osDiskSizeGB: 64
        type: 'VirtualMachineScaleSets'
      }
    ]
    networkProfile: {
      networkPlugin: 'azure'
      serviceCidr: '10.0.0.0/16'
      dnsServiceIP: '10.0.0.10'
      loadBalancerSku: 'standard'
    }
    addonProfiles: {
      omsagent: enableContainerInsights
        ? {
            enabled: true
            config: {
              logAnalyticsWorkspaceResourceID: logAnalyticsWorkspaceId
            }
          }
        : {
            enabled: false
          }
    }
  }
  dependsOn: [
    networkContributorRoleAssignment
  ]
}

// AcrPull role definition ID: 7f951dda-4ed3-4680-a7ca-43fe172d538d
var acrPullRoleDefinitionId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')

// Existing ACR resource reference for role assignment scope
resource targetAcr 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: acrName
}

// Assign AcrPull permission to the AKS Kubelet Identity
resource acrPullRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: targetAcr
  name: guid(aksCluster.id, targetAcr.id, acrPullRoleDefinitionId)
  properties: {
    roleDefinitionId: acrPullRoleDefinitionId
    principalId: aksCluster.properties.identityProfile.kubeletidentity.objectId
    principalType: 'ServicePrincipal'
  }
}

output clusterId string = aksCluster.id
output clusterName string = aksCluster.name
output kubeletIdentityObjectId string = aksCluster.properties.identityProfile.kubeletidentity.objectId
