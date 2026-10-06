@description('Azure region for AKS')
param location string

@description('AKS Cluster name')
param clusterName string

@description('Subnet Resource ID for AKS cluster')
param subnetId string

@description('Log Analytics Workspace Resource ID for Container Insights')
param logAnalyticsWorkspaceId string

@description('Azure Container Registry Resource ID for AcrPull attachment')
param acrId string

@description('VM Size for worker nodes')
param vmSize string = 'Standard_D4s_v5'

@description('Initial node count')
param nodeCount int = 2

@description('Minimum node count for autoscaling')
param minNodeCount int = 2

@description('Maximum node count for autoscaling (FinOps limit)')
param maxNodeCount int = 3

@description('Kubernetes version (defaults to supported version)')
param kubernetesVersion string = '1.29'

@description('Resource tags')
param tags object = {}

// Managed Identity for AKS Control Plane
resource aksIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${clusterName}-identity'
  location: location
  tags: tags
}

// AKS Cluster
resource aksCluster 'Microsoft.ContainerService/managedClusters@2024-02-01' = {
  name: clusterName
  location: location
  tags: tags
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
        enableAutoScaling: true
        minCount: minNodeCount
        maxCount: maxNodeCount
        osDiskSizeGB: 64
        type: 'VirtualMachineScaleSets'
      }
    ]
    networkProfile: {
      networkPlugin: 'azure'
      networkPolicy: 'azure'
      serviceCidr: '10.0.0.0/16'
      dnsServiceIP: '10.0.0.10'
      loadBalancerSku: 'standard'
    }
    addonProfiles: {
      omsagent: {
        enabled: true
        config: {
          logAnalyticsWorkspaceResourceID: logAnalyticsWorkspaceId
        }
      }
    }
  }
}

// AcrPull role definition ID: 7f951dda-4ed3-4680-a7ca-43fe172d538d
var acrPullRoleDefinitionId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')

// Existing ACR resource reference for role assignment scope
resource targetAcr 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: last(split(acrId, '/'))
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
output oidcIssuerUrl string = aksCluster.properties.oidcIssuerProfile.issuerURL
