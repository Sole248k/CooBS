// Two networks joined by peering: the cloud VNet, and a second VNet that stands in for the
// on-premises datacenter. The NSG on the on-premises subnet stands in for its firewall.

@description('Azure region for all network resources')
param location string

@description('Cloud virtual network name')
param cloudVnetName string = 'vnet-coobs-cloud'

@description('On-premises (simulated) virtual network name')
param onPremVnetName string = 'vnet-coobs-onprem'

@description('Cloud VNet address prefix')
param cloudVnetPrefix string = '10.240.0.0/16'

@description('AKS nodes and pods. The Kafka internal load balancer takes a fixed address in here')
param aksSubnetPrefix string = '10.240.0.0/20'

@description('Private endpoints (Azure Managed Redis)')
param privateEndpointSubnetPrefix string = '10.240.16.0/24'

@description('On-premises VNet address prefix')
param onPremVnetPrefix string = '10.250.0.0/16'

@description('The one on-premises subnet: the VM running ledger, account, audit, batch and their databases')
param onPremSubnetPrefix string = '10.250.0.0/24'

@description('Ports of account, ledger, audit and batch (contracts/runtime/RUNTIME_SETTINGS.md)')
param onPremServicePorts array = [
  '8082'
  '8083'
  '8085'
  '8093'
]

@description('Resource tags')
param tags object = {}

// =============================================================================
// On-premises firewall
// =============================================================================
// The default rules let a peered VNet reach every port, so the deny rule is what makes this a
// firewall: the cluster reaches the four service ports and nothing else (not Oracle, not SSH).
// For the link-down check, add a Deny rule above priority 100.
resource nsgOnPrem 'Microsoft.Network/networkSecurityGroups@2023-09-01' = {
  name: 'nsg-coobs-onprem'
  location: location
  tags: tags
  properties: {
    securityRules: [
      {
        name: 'Allow-Cluster-To-Core-Services'
        properties: {
          priority: 100
          direction: 'Inbound'
          access: 'Allow'
          protocol: 'Tcp'
          sourceAddressPrefix: aksSubnetPrefix
          sourcePortRange: '*'
          destinationAddressPrefix: onPremSubnetPrefix
          destinationPortRanges: onPremServicePorts
        }
      }
      {
        name: 'Deny-Everything-Else-From-Cloud'
        properties: {
          priority: 4000
          direction: 'Inbound'
          access: 'Deny'
          protocol: '*'
          sourceAddressPrefix: cloudVnetPrefix
          sourcePortRange: '*'
          destinationAddressPrefix: '*'
          destinationPortRange: '*'
        }
      }
    ]
  }
}

// =============================================================================
// On-premises outbound
// =============================================================================
// The VM has no public address. It still has to pull its images (the registry, Oracle's
// registry, Docker Hub), and a new subnet has no outbound route of its own.
resource natPublicIp 'Microsoft.Network/publicIPAddresses@2023-09-01' = {
  name: 'pip-coobs-onprem-nat'
  location: location
  tags: tags
  sku: {
    name: 'Standard'
  }
  properties: {
    publicIPAllocationMethod: 'Static'
  }
}

resource natGateway 'Microsoft.Network/natGateways@2023-09-01' = {
  name: 'nat-coobs-onprem'
  location: location
  tags: tags
  sku: {
    name: 'Standard'
  }
  properties: {
    idleTimeoutInMinutes: 4
    publicIpAddresses: [
      {
        id: natPublicIp.id
      }
    ]
  }
}

// =============================================================================
// Virtual networks
// =============================================================================
// snet-aks carries no NSG of its own: AKS keeps one on the node interfaces and opens it for
// each LoadBalancer Service. A second one here would silently block the public gateway.
resource cloudVnet 'Microsoft.Network/virtualNetworks@2024-05-01' = {
  name: cloudVnetName
  location: location
  tags: tags
  properties: {
    addressSpace: {
      addressPrefixes: [
        cloudVnetPrefix
      ]
    }
    subnets: [
      {
        name: 'snet-aks'
        properties: {
          addressPrefix: aksSubnetPrefix
          serviceEndpoints: [
            {
              service: 'Microsoft.Sql'
            }
          ]
        }
      }
      {
        name: 'snet-private-endpoints'
        properties: {
          addressPrefix: privateEndpointSubnetPrefix
        }
      }
    ]
  }
}

resource onPremVnet 'Microsoft.Network/virtualNetworks@2024-05-01' = {
  name: onPremVnetName
  location: location
  tags: tags
  properties: {
    addressSpace: {
      addressPrefixes: [
        onPremVnetPrefix
      ]
    }
    subnets: [
      {
        name: 'snet-core'
        properties: {
          addressPrefix: onPremSubnetPrefix
          defaultOutboundAccess: false
          networkSecurityGroup: {
            id: nsgOnPrem.id
          }
          natGateway: {
            id: natGateway.id
          }
        }
      }
    ]
  }
}

// =============================================================================
// The hybrid link (VPN gateways in production, peering here)
// =============================================================================
resource peerCloudToOnPrem 'Microsoft.Network/virtualNetworks/virtualNetworkPeerings@2024-05-01' = {
  parent: cloudVnet
  name: 'peer-cloud-to-onprem'
  properties: {
    remoteVirtualNetwork: {
      id: onPremVnet.id
    }
    allowVirtualNetworkAccess: true
    allowForwardedTraffic: false
    allowGatewayTransit: false
    useRemoteGateways: false
  }
}

resource peerOnPremToCloud 'Microsoft.Network/virtualNetworks/virtualNetworkPeerings@2024-05-01' = {
  parent: onPremVnet
  name: 'peer-onprem-to-cloud'
  properties: {
    remoteVirtualNetwork: {
      id: cloudVnet.id
    }
    allowVirtualNetworkAccess: true
    allowForwardedTraffic: false
    allowGatewayTransit: false
    useRemoteGateways: false
  }
  dependsOn: [
    peerCloudToOnPrem
  ]
}

output cloudVnetId string = cloudVnet.id
output cloudVnetName string = cloudVnet.name
output onPremVnetId string = onPremVnet.id
output onPremVnetName string = onPremVnet.name
output aksSubnetId string = '${cloudVnet.id}/subnets/snet-aks'
output privateEndpointSubnetId string = '${cloudVnet.id}/subnets/snet-private-endpoints'
output onPremSubnetId string = '${onPremVnet.id}/subnets/snet-core'
output natPublicIpAddress string = natPublicIp.properties.ipAddress
