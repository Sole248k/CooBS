@description('Azure region for Event Hubs')
param location string

@description('Event Hubs Namespace name (alphanumeric and hyphens)')
param namespaceName string

@description('Throughput units (1 TU = 1 MB/s ingress, 2 MB/s egress)')
param capacity int = 1

@description('Maximum throughput units for FinOps Auto-Inflate protection')
param maxThroughputUnits int = 2

@description('Resource tags')
param tags object = {}

// List of core banking Kafka topics (and DLT twins)
var topics = [
  'transaction.stage.changed.v1'
  'transaction.stage.changed.v1.DLT'
  'ledger.mutation.completed.v1'
  'ledger.mutation.completed.v1.DLT'
  'ledger.transfer.completed.v1'
  'ledger.transfer.completed.v1.DLT'
  'ledger.transfer.failed.v1'
  'ledger.transfer.failed.v1.DLT'
  'account.closure.completed.v1'
  'account.closure.completed.v1.DLT'
  'account.created.v1'
  'kyc.request.submitted.v1'
  'kyc.request.evaluated.v1'
]

var consumerGroups = [
  'notification-service-group'
  'transaction-service-group'
  'orchestration-service-group'
  'audit-service-group'
]

resource eventHubNamespace 'Microsoft.EventHub/namespaces@2024-01-01' = {
  name: namespaceName
  location: location
  sku: {
    name: 'Standard'
    tier: 'Standard'
    capacity: capacity
  }
  tags: tags
  properties: {
    isAutoInflateEnabled: true
    maximumThroughputUnits: maxThroughputUnits
    kafkaEnabled: true
    minimumTlsVersion: '1.2'
    disableLocalAuth: false
  }
}

// Provision Kafka topics as individual Event Hubs
resource eventHubTopics 'Microsoft.EventHub/namespaces/eventhubs@2024-01-01' = [for topic in topics: {
  parent: eventHubNamespace
  name: topic
  properties: {
    messageRetentionInDays: 1
    partitionCount: 3
  }
}]

// Provision Consumer Groups for transaction.stage.changed.v1
resource stageChangedHub 'Microsoft.EventHub/namespaces/eventhubs@2024-01-01' existing = {
  parent: eventHubNamespace
  name: 'transaction.stage.changed.v1'
  dependsOn: [
    eventHubTopics
  ]
}

resource stageConsumerGroups 'Microsoft.EventHub/namespaces/eventhubs/consumergroups@2024-01-01' = [for cg in consumerGroups: {
  parent: stageChangedHub
  name: cg
}]

// Authorization rule for Spring Boot services using SASL_SSL Kafka wire protocol
resource authRule 'Microsoft.EventHub/namespaces/authorizationRules@2024-01-01' = {
  parent: eventHubNamespace
  name: 'RootManageSharedAccessKey'
  properties: {
    rights: [
      'Listen'
      'Send'
      'Manage'
    ]
  }
}

output namespaceId string = eventHubNamespace.id
output namespaceName string = eventHubNamespace.name
output kafkaBootstrapServer string = '${eventHubNamespace.name}.servicebus.windows.net:9093'
output primaryConnectionString string = authRule.listKeys().primaryConnectionString
