@description('Azure region for all network resources')
param location string

@description('Virtual Network name')
param vnetName string = 'vnet-coobs'

@description('VNet address prefix')
param vnetAddressPrefix string = '10.240.0.0/16'

@description('Subnet prefix for Azure Kubernetes Service node & pod IPs')
param aksSubnetPrefix string = '10.240.0.0/20'

@description('Subnet prefix for PostgreSQL Flexible Server delegated subnet')
param postgresSubnetPrefix string = '10.240.16.0/24'

@description('Subnet prefix for Application Gateway / Ingress')
param appGwSubnetPrefix string = '10.240.17.0/24'

@description('Resource tags')
param tags object = {}

// =============================================================================
// Network Security Groups (NSGs)
// =============================================================================
resource nsgAks 'Microsoft.Network/networkSecurityGroups@2023-09-01' = {
  name: 'nsg-aks-coobs'
  location: location
  tags: tags
  properties: {
    securityRules: [
      {
        name: 'Allow-Internal-Cluster'
        properties: {
          priority: 100
          direction: 'Inbound'
          access: 'Allow'
          protocol: '*'
          sourceAddressPrefix: vnetAddressPrefix
          sourcePortRange: '*'
          destinationAddressPrefix: vnetAddressPrefix
          destinationPortRange: '*'
        }
      }
    ]
  }
}

resource nsgPostgres 'Microsoft.Network/networkSecurityGroups@2023-09-01' = {
  name: 'nsg-postgres-coobs'
  location: location
  tags: tags
  properties: {
    securityRules: [
      {
        name: 'Allow-AKS-to-Postgres'
        properties: {
          priority: 100
          direction: 'Inbound'
          access: 'Allow'
          protocol: 'Tcp'
          sourceAddressPrefix: aksSubnetPrefix
          sourcePortRange: '*'
          destinationAddressPrefix: postgresSubnetPrefix
          destinationPortRange: '5432'
        }
      }
    ]
  }
}

resource nsgAppGw 'Microsoft.Network/networkSecurityGroups@2023-09-01' = {
  name: 'nsg-appgw-coobs'
  location: location
  tags: tags
  properties: {
    securityRules: [
      {
        name: 'Allow-Gateway-Manager'
        properties: {
          priority: 100
          direction: 'Inbound'
          access: 'Allow'
          protocol: 'Tcp'
          sourceAddressPrefix: 'GatewayManager'
          sourcePortRange: '*'
          destinationAddressPrefix: '*'
          destinationPortRange: '65200-65535'
        }
      }
      {
        name: 'Allow-HTTP-HTTPS-Inbound'
        properties: {
          priority: 110
          direction: 'Inbound'
          access: 'Allow'
          protocol: 'Tcp'
          sourceAddressPrefix: 'Internet'
          sourcePortRange: '*'
          destinationAddressPrefix: '*'
          destinationPortRanges: [
            '80'
            '443'
          ]
        }
      }
    ]
  }
}

// =============================================================================
// Virtual Network & Subnets
// =============================================================================
resource vnet 'Microsoft.Network/virtualNetworks@2023-09-01' = {
  name: vnetName
  location: location
  tags: tags
  properties: {
    addressSpace: {
      addressPrefixes: [
        vnetAddressPrefix
      ]
    }
    subnets: [
      {
        name: 'snet-aks'
        properties: {
          addressPrefix: aksSubnetPrefix
          networkSecurityGroup: {
            id: nsgAks.id
          }
        }
      }
      {
        name: 'snet-postgres'
        properties: {
          addressPrefix: postgresSubnetPrefix
          networkSecurityGroup: {
            id: nsgPostgres.id
          }
          delegations: [
            {
              name: 'delegation-postgres'
              properties: {
                serviceName: 'Microsoft.DBforPostgreSQL/flexibleServers'
              }
            }
          ]
        }
      }
      {
        name: 'snet-appgw'
        properties: {
          addressPrefix: appGwSubnetPrefix
          networkSecurityGroup: {
            id: nsgAppGw.id
          }
        }
      }
    ]
  }
}

// =============================================================================
// Private DNS Zone for PostgreSQL Flexible Server
// =============================================================================
resource privateDnsZonePostgres 'Microsoft.Network/privateDnsZones@2020-06-01' = {
  name: 'coobs.postgres.database.azure.com'
  location: 'global'
  tags: tags
}

resource privateDnsZoneLinkPostgres 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2020-06-01' = {
  parent: privateDnsZonePostgres
  name: '${vnetName}-dns-link'
  location: 'global'
  properties: {
    registrationEnabled: false
    virtualNetwork: {
      id: vnet.id
    }
  }
}

// Outputs
output vnetId string = vnet.id
output vnetName string = vnet.name
output aksSubnetId string = '${vnet.id}/subnets/snet-aks'
output postgresSubnetId string = '${vnet.id}/subnets/snet-postgres'
output appGwSubnetId string = '${vnet.id}/subnets/snet-appgw'
output postgresPrivateDnsZoneId string = privateDnsZonePostgres.id
