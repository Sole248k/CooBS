@description('Azure region for ACR')
param location string

@description('Azure Container Registry globally unique name (alphanumeric only)')
param acrName string

@description('ACR SKU (Basic, Standard, Premium)')
@allowed([
  'Basic'
  'Standard'
  'Premium'
])
param sku string = 'Basic'

@description('Enable admin user')
param adminUserEnabled bool = true

@description('Resource tags')
param tags object = {}

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: sku
  }
  tags: tags
  properties: {
    adminUserEnabled: adminUserEnabled
  }
}

output acrId string = acr.id
output acrName string = acr.name
output loginServer string = acr.properties.loginServer
