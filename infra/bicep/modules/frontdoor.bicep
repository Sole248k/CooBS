// Front Door (Standard): HTTPS at the edge, in front of the one public address of the cluster.
//
// Off by default (deployFrontDoor in main.bicep). It cannot be part of the first deployment:
// its origin is the address Azure gives the coobs-public load balancer, and that address exists
// only after infra/scripts/deploy-cloud.ps1 has applied the manifests. So it is switched on by
// deploying again with the address, once E2E has passed on plain HTTP.
//
// One route sends everything to that one origin. The console's nginx is behind it and passes
// /api on to the gateway, so pages and API calls share the Front Door host name.

@description('Front Door profile name')
param profileName string

@description('Endpoint name. The public host name is <this>-<hash>.<zone>.azurefd.net')
param endpointName string

@description('Public address of the coobs-public load balancer, as printed by deploy-cloud.ps1')
param originHost string

@description('Resource tags')
param tags object = {}

resource profile 'Microsoft.Cdn/profiles@2024-02-01' = {
  name: profileName
  location: 'global'
  tags: tags
  sku: {
    name: 'Standard_AzureFrontDoor'
  }
}

resource endpoint 'Microsoft.Cdn/profiles/afdEndpoints@2024-02-01' = {
  parent: profile
  name: endpointName
  location: 'global'
  tags: tags
  properties: {
    enabledState: 'Enabled'
  }
}

resource originGroup 'Microsoft.Cdn/profiles/originGroups@2024-02-01' = {
  parent: profile
  name: 'coobs-public'
  properties: {
    loadBalancingSettings: {
      sampleSize: 4
      successfulSamplesRequired: 3
      additionalLatencyInMilliseconds: 50
    }
    // The console's start page. Probed rarely: there is one origin, so nothing fails over.
    healthProbeSettings: {
      probePath: '/'
      probeRequestType: 'HEAD'
      probeProtocol: 'Http'
      probeIntervalInSeconds: 120
    }
    sessionAffinityState: 'Disabled'
  }
}

resource origin 'Microsoft.Cdn/profiles/originGroups/origins@2024-02-01' = {
  parent: originGroup
  name: 'cluster-load-balancer'
  properties: {
    hostName: originHost
    originHostHeader: originHost
    httpPort: 80
    httpsPort: 443
    priority: 1
    weight: 1000
    enabledState: 'Enabled'
    // The origin speaks plain HTTP only, so there is no certificate to check.
    enforceCertificateNameCheck: false
  }
}

resource route 'Microsoft.Cdn/profiles/afdEndpoints/routes@2024-02-01' = {
  parent: endpoint
  name: 'everything'
  dependsOn: [
    origin
  ]
  properties: {
    originGroup: {
      id: originGroup.id
    }
    supportedProtocols: [
      'Http'
      'Https'
    ]
    patternsToMatch: [
      '/*'
    ]
    // HTTPS ends here. From Front Door to the load balancer the call is HTTP, inside Azure.
    forwardingProtocol: 'HttpOnly'
    httpsRedirect: 'Enabled'
    linkToDefaultDomain: 'Enabled'
    enabledState: 'Enabled'
  }
}

output hostName string = endpoint.properties.hostName
