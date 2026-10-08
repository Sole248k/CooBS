@description('Azure region for monitoring resources')
param location string

@description('Log Analytics Workspace name')
param workspaceName string = 'law-coobs-dev'

@description('Application Insights name')
param appInsightsName string = 'appi-coobs-dev'

@description('Daily ingestion quota in GB (FinOps cap: 0.5 GB/day to prevent runaway costs)')
param dailyQuotaGb int = 1

@description('Retention in days (30 days included in free ingestion)')
param retentionInDays int = 30

@description('Resource tags')
param tags object = {}

resource logAnalyticsWorkspace 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: workspaceName
  location: location
  tags: tags
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: retentionInDays
    workspaceCapping: {
      dailyQuotaGb: dailyQuotaGb
    }
  }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: appInsightsName
  location: location
  kind: 'web'
  tags: tags
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: logAnalyticsWorkspace.id
    IngestionMode: 'LogAnalytics'
  }
}

output workspaceId string = logAnalyticsWorkspace.id
output workspaceCustomerId string = logAnalyticsWorkspace.properties.customerId
output appInsightsId string = appInsights.id
output appInsightsConnectionString string = appInsights.properties.ConnectionString
