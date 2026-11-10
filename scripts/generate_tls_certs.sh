#!/usr/bin/env bash
# ==============================================================================
# CooBS Banking Platform - TLS Certificate & Keystore Generator
# Task: FIN-807 - Production Cloud Hardening (TLS / Key Vault)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

OUTPUT_DIR="${1:-infra/certs}"
KEYSTORE_PASS="${KEYSTORE_PASS:-changeit}"
ALIAS="${ALIAS:-coobs-gateway}"
VALIDITY_DAYS="${VALIDITY_DAYS:-365}"
TARGET_DIR="$WORKSPACE_ROOT/$OUTPUT_DIR"

mkdir -p "$TARGET_DIR"

echo "================================================================="
echo "    CooBS Banking Platform - TLS Certificate & Keystore Generator"
echo "    Task FIN-807: Production Cloud Hardening (TLS / Key Vault)   "
echo "================================================================="

KEYSTORE_PATH="$TARGET_DIR/keystore.p12"
CERT_PATH="$TARGET_DIR/tls.crt"
KEY_PATH="$TARGET_DIR/tls.key"

if command -v keytool >/dev/null 2>&1; then
    echo "[*] Using keytool utility..."
    [ -f "$KEYSTORE_PATH" ] && rm -f "$KEYSTORE_PATH"

    DNAME="CN=coobs-gateway.southeastasia.cloudapp.azure.com, OU=Platform Engineering, O=CooBS Retail Banking, L=Singapore, ST=SG, C=SG"
    SAN="SAN=dns:localhost,dns:api.coobs.internal,dns:coobs-gateway,dns:coobs-gateway.southeastasia.cloudapp.azure.com,ip:127.0.0.1"

    echo "[1/3] Generating 2048-bit RSA PKCS12 keystore..."
    keytool -genkeypair \
        -alias "$ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity "$VALIDITY_DAYS" \
        -keystore "$KEYSTORE_PATH" \
        -storetype PKCS12 \
        -storepass "$KEYSTORE_PASS" \
        -dname "$DNAME" \
        -ext "$SAN"

    echo "[2/3] Exporting X.509 RFC-1421 / PEM certificate..."
    keytool -exportcert \
        -alias "$ALIAS" \
        -keystore "$KEYSTORE_PATH" \
        -storepass "$KEYSTORE_PASS" \
        -rfc \
        -file "$CERT_PATH"

elif command -v openssl >/dev/null 2>&1; then
    echo "[*] Using openssl utility..."
    echo "[1/3] Generating self-signed RSA private key and certificate..."
    openssl req -x509 -nodes -days "$VALIDITY_DAYS" -newkey rsa:2048 \
        -keyout "$KEY_PATH" \
        -out "$CERT_PATH" \
        -subj "/C=SG/ST=SG/L=Singapore/O=CooBS Retail Banking/OU=Platform Engineering/CN=coobs-gateway.southeastasia.cloudapp.azure.com" \
        -addext "subjectAltName=DNS:localhost,DNS:api.coobs.internal,DNS:coobs-gateway,IP:127.0.0.1"

    echo "[2/3] Exporting PKCS12 keystore for Spring Boot..."
    openssl pkcs12 -export \
        -in "$CERT_PATH" \
        -inkey "$KEY_PATH" \
        -out "$KEYSTORE_PATH" \
        -name "$ALIAS" \
        -passout "pass:$KEYSTORE_PASS"
else
    echo "ERROR: Neither 'keytool' nor 'openssl' was found in PATH." >&2
    exit 1
fi

GATEWAY_DIR="$WORKSPACE_ROOT/backend/api-gateway/src/main/resources/keystore"
mkdir -p "$GATEWAY_DIR"
cp -f "$KEYSTORE_PATH" "$GATEWAY_DIR/keystore.p12"
echo "[3/3] Synchronized keystore to $GATEWAY_DIR/keystore.p12"

echo ""
echo "================================================================="
echo "               TLS PROVISIONING COMPLETE (FIN-807)               "
echo "================================================================="
echo "Generated Artifacts:"
echo "  - Keystore:    $KEYSTORE_PATH"
echo "  - Public Cert: $CERT_PATH"
echo ""
echo "Activate in Spring Boot Gateway via:"
echo "  export SSL_ENABLED=true"
echo "  export SERVER_PORT=8443"
echo "  export SSL_KEY_STORE=classpath:keystore/keystore.p12"
echo "================================================================="
