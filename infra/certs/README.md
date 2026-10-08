# TLS Certificates & Local Keystores

This directory holds TLS certificates, private keys, and PKCS12 keystores used for HTTPS encryption at the API Gateway and Kubernetes Ingress layers.

## Generating Local Certificates

To generate a new self-signed certificate and PKCS12 keystore for local development or staging:

### Windows PowerShell:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/generate_tls_certs.ps1 -CopyToGateway
```

### Linux / macOS:
```bash
bash scripts/generate_tls_certs.sh
```

## Production Azure Key Vault Import

In production, certificates are managed centrally in Azure Key Vault and injected via the Secrets Store CSI driver:

```bash
az keyvault certificate import \
  --vault-name kv-coobs-dev \
  --name coobs-tls-cert \
  --file infra/certs/keystore.p12 \
  --password changeit
```

## Activating HTTPS on API Gateway

Set the following environment variables:
```bash
SSL_ENABLED=true
SERVER_PORT=8443
SSL_KEY_STORE=classpath:keystore/keystore.p12
SSL_KEY_STORE_PASSWORD=changeit
```
