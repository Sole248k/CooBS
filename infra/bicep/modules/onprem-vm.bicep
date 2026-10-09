// The simulated datacenter: one VM with no public address, running Docker Compose with the
// four on-premises services, Oracle and the audit PostgreSQL.

@description('Azure region for the VM')
param location string

@description('VM name')
param vmName string = 'vm-coobs-onprem'

@description('2 vCPUs and 8 GB. With the AKS node this uses the whole 4 vCPU quota of the trial')
param vmSize string = 'Standard_B2as_v2'

@description('The only zone in which the trial subscription may create this size')
param availabilityZone string = '3'

@description('On-premises subnet')
param subnetId string

@description('Fixed private address. infra/k8s/config.yaml (ONPREM_VM_IP) must carry the same value')
param privateIpAddress string = '10.250.0.4'

@description('Administrator user name')
param adminUsername string = 'coobsadmin'

@description('SSH public key of the administrator. Password sign-in is off')
param adminSshPublicKey string

@description('Existing container registry the VM pulls its four service images from')
param acrName string

@description('Key Vault the VM reads its secrets from')
param keyVaultName string

@description('OS disk size. Oracle Database Free and its data take most of it')
param osDiskSizeGb int = 64

@description('Resource tags')
param tags object = {}

var cloudInit = replace(loadTextContent('../cloud-init/onprem-vm.yaml'), '__ADMIN_USER__', adminUsername)

resource nic 'Microsoft.Network/networkInterfaces@2023-09-01' = {
  name: 'nic-${vmName}'
  location: location
  tags: tags
  properties: {
    ipConfigurations: [
      {
        name: 'ipconfig1'
        properties: {
          privateIPAllocationMethod: 'Static'
          privateIPAddress: privateIpAddress
          subnet: {
            id: subnetId
          }
        }
      }
    ]
  }
}

resource vm 'Microsoft.Compute/virtualMachines@2024-03-01' = {
  name: vmName
  location: location
  tags: tags
  zones: [
    availabilityZone
  ]
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    hardwareProfile: {
      vmSize: vmSize
    }
    storageProfile: {
      imageReference: {
        publisher: 'Canonical'
        offer: 'ubuntu-24_04-lts'
        sku: 'server'
        version: 'latest'
      }
      osDisk: {
        createOption: 'FromImage'
        diskSizeGB: osDiskSizeGb
        managedDisk: {
          storageAccountType: 'StandardSSD_LRS'
        }
      }
    }
    osProfile: {
      computerName: 'coobs-onprem'
      adminUsername: adminUsername
      customData: base64(cloudInit)
      linuxConfiguration: {
        disablePasswordAuthentication: true
        ssh: {
          publicKeys: [
            {
              path: '/home/${adminUsername}/.ssh/authorized_keys'
              keyData: adminSshPublicKey
            }
          ]
        }
      }
    }
    networkProfile: {
      networkInterfaces: [
        {
          id: nic.id
        }
      ]
    }
    diagnosticsProfile: {
      bootDiagnostics: {
        enabled: true
      }
    }
  }
}

// AcrPull role definition ID: 7f951dda-4ed3-4680-a7ca-43fe172d538d
var acrPullRoleDefinitionId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')

resource targetAcr 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: acrName
}

resource acrPullRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: targetAcr
  name: guid(vm.id, targetAcr.id, acrPullRoleDefinitionId)
  properties: {
    roleDefinitionId: acrPullRoleDefinitionId
    principalId: vm.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// The VM reads its own secrets (JWT, database passwords, the Redis key) when it starts the
// Compose stack, so none of them is typed into a command or copied to it by hand.
// Key Vault Secrets User: 4633458b-17de-408a-b874-0445c86b69e6
var secretsUserRoleDefinitionId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4633458b-17de-408a-b874-0445c86b69e6')

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: keyVaultName
}

resource secretsUserRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: keyVault
  name: guid(vm.id, keyVault.id, secretsUserRoleDefinitionId)
  properties: {
    roleDefinitionId: secretsUserRoleDefinitionId
    principalId: vm.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

output vmName string = vm.name
output privateIpAddress string = privateIpAddress
