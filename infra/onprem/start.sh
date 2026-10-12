#!/bin/sh
# Starts the on-premises zone on the VM. Runs from /opt/coobs, as root.
#
#   start.sh <key vault name> <registry login server> <image tag> <redis host> [kafka bootstrap]
#   start.sh status
#
# It reads the secrets from Key Vault with the VM's managed identity (the identity holds
# Key Vault Secrets User), writes them to .env beside the Compose file, signs Docker in to the
# registry, pulls the images and starts everything. No secret is passed on the command line.
set -eu
cd "$(dirname "$0")"

if [ "${1:-}" = "status" ]; then
  docker ps --all --format 'table {{.Names}}\t{{.Status}}'
  exit 0
fi

VAULT="$1"
REGISTRY="$2"
IMAGE_TAG="$3"
REDIS_HOST="$4"
KAFKA_BOOTSTRAP_SERVERS="${5:-10.240.15.200:9094}"

TOKEN=$(curl -fsS -H Metadata:true \
  "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https%3A%2F%2Fvault.azure.net" \
  | jq -r .access_token)

# Prints one secret. A missing or empty secret stops the script: nothing is started half-configured.
secret() {
  value=$(curl -fsS -H "Authorization: Bearer $TOKEN" \
    "https://$VAULT.vault.azure.net/secrets/$1?api-version=7.4" | jq -r .value) || {
    echo "Key Vault secret '$1' could not be read from $VAULT" >&2
    exit 1
  }
  if [ -z "$value" ] || [ "$value" = "null" ]; then
    echo "Key Vault secret '$1' is empty in $VAULT" >&2
    exit 1
  fi
  case "$value" in
    *"'"*) echo "Key Vault secret '$1' holds a single quote, which the .env file cannot carry" >&2; exit 1 ;;
  esac
  printf '%s' "$value"
}

JWT_SECRET=$(secret jwt-secret)
ORACLE_PASSWORD=$(secret oracle-password)
APP_USER_PASSWORD=$(secret app-user-password)
POSTGRES_PASSWORD=$(secret audit-db-password)
REDIS_PASSWORD=$(secret redis-key)

# Monitoring is optional. With no datadog-api-key in the vault the answer is empty, the
# "monitoring" profile stays off and the Datadog agent is not started; nothing else changes.
optional_secret() {
  curl -fsS -H "Authorization: Bearer $TOKEN" \
    "https://$VAULT.vault.azure.net/secrets/$1?api-version=7.4" 2>/dev/null | jq -r '.value // empty' || true
}
DD_API_KEY=$(optional_secret datadog-api-key)
DD_SITE=$(optional_secret datadog-site)
COMPOSE_PROFILES=""
if [ -n "$DD_API_KEY" ]; then COMPOSE_PROFILES=monitoring; fi

umask 077
cat > .env <<EOF
REGISTRY='$REGISTRY'
IMAGE_TAG='$IMAGE_TAG'
KAFKA_BOOTSTRAP_SERVERS='$KAFKA_BOOTSTRAP_SERVERS'
REDIS_HOST='$REDIS_HOST'
JWT_SECRET='$JWT_SECRET'
ORACLE_PASSWORD='$ORACLE_PASSWORD'
APP_USER_PASSWORD='$APP_USER_PASSWORD'
POSTGRES_PASSWORD='$POSTGRES_PASSWORD'
REDIS_PASSWORD='$REDIS_PASSWORD'
COMPOSE_PROFILES='$COMPOSE_PROFILES'
DD_API_KEY='$DD_API_KEY'
DD_SITE='${DD_SITE:-datadoghq.com}'
EOF

acr-login "$REGISTRY" > /dev/null
docker compose pull --quiet
docker compose up -d --quiet-pull
docker ps --all --format 'table {{.Names}}\t{{.Status}}'
