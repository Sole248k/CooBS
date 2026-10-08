#!/usr/bin/env bash
# ==============================================================================
# setup.sh: One-click Setup & Bootstrap Script for Local Data Foundation (Phase 1)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "================================================================="
echo " Core Retail Ledger - Phase 1: Local Data Foundation Setup       "
echo "================================================================="

# 1. Check Docker prerequisite
echo "[1/5] Checking Docker and Docker Compose..."
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed or not in PATH."
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "ERROR: Docker daemon is not running. Please start Docker and retry."
    exit 1
fi

# 2. Check and copy .env file
echo "[2/5] Checking environment configuration (.env)..."
if [ ! -f "${ROOT_DIR}/.env" ]; then
    echo "  .env not found. Copying from .env.example..."
    cp "${ROOT_DIR}/.env.example" "${ROOT_DIR}/.env"
    echo "  Created active .env file."
else
    echo "  Existing .env found."
fi

# 3. Spin up docker-compose services
echo "[3/5] Starting Data Foundation containers..."
(cd "${ROOT_DIR}" && docker compose up -d)

# 4. Wait for services to become healthy
echo "[4/5] Waiting for datastores to become healthy (initial cold boot ~45-60s)..."
MAX_ATTEMPTS=40
ATTEMPT=1
ALL_HEALTHY=false

while [ "${ATTEMPT}" -le "${MAX_ATTEMPTS}" ]; do
    sleep 3
    
    ORA_STATUS=$(docker exec oracle-core-db bash -c 'echo "SELECT 1 FROM DUAL;" | sqlplus -s ${APP_USER}/${APP_USER_PASSWORD}@localhost:1521/${ORACLE_DATABASE}' 2>&1 || true)
    PG_STATUS=$(docker exec postgres-audit-db bash -c 'pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}' 2>&1 || true)

    if echo "${ORA_STATUS}" | grep -q "1" && echo "${PG_STATUS}" | grep -q "accepting connections"; then
        ALL_HEALTHY=true
        break
    fi

    echo "  [Attempt ${ATTEMPT}/${MAX_ATTEMPTS}] Waiting for Oracle and PostgreSQL..."
    ATTEMPT=$((ATTEMPT + 1))
done

if [ "${ALL_HEALTHY}" = "true" ]; then
    echo "All core datastores initialized successfully!"
else
    echo "WARNING: Datastores took longer than expected to initialize. Running verification to inspect detailed statuses..."
fi

# 5. Run Verification Script
echo "[5/5] Executing verification checks..."
bash "${SCRIPT_DIR}/verify.sh"

echo "================================================================="
echo " Local Data Foundation is Ready for Development!                 "
echo " Kafka UI: http://localhost:8085                                 "
echo "================================================================="