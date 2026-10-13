// A spending alert for the demo resource group. Deployed by itself, so it never touches the
// running deployment:
//
//   az deployment group create --resource-group CooBS-rsg --name coobs-budget --template-file infra/bicep/budget.bicep --parameters contactEmail=<an address you read>
//
// A budget warns; it stops nothing. On pay-as-you-go there is no spending limit, so the only
// thing that ends the cost is deleting or stopping the resources (infra/README.md, "Removing it").
targetScope = 'resourceGroup'

@description('Where the alerts are sent')
param contactEmail string

@description('Monthly amount in the billing currency. The demo runs at about 10 a day')
param amount int = 60

@description('First day of the month the budget starts in, as yyyy-MM-01')
param startDate string = '${utcNow('yyyy-MM')}-01'

resource budget 'Microsoft.Consumption/budgets@2023-11-01' = {
  name: 'budget-coobs-demo'
  properties: {
    category: 'Cost'
    amount: amount
    timeGrain: 'Monthly'
    timePeriod: {
      startDate: startDate
    }
    notifications: {
      actualHalf: {
        enabled: true
        operator: 'GreaterThan'
        threshold: 50
        thresholdType: 'Actual'
        contactEmails: [contactEmail]
      }
      actualNinety: {
        enabled: true
        operator: 'GreaterThan'
        threshold: 90
        thresholdType: 'Actual'
        contactEmails: [contactEmail]
      }
      forecastFull: {
        enabled: true
        operator: 'GreaterThan'
        threshold: 100
        thresholdType: 'Forecasted'
        contactEmails: [contactEmail]
      }
    }
  }
}
