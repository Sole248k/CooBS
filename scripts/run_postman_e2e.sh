#!/usr/bin/env bash
# ==============================================================================
# run_postman_e2e.sh: Automated Postman Newman E2E Regression Runner
# Role: Day 5 CI/CD Checkpoint & Platform E2E Regression (11 Services)
# Reference: DEV8_DEVOPS_WEEKLY_PLAN.md (Task 5.3)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "${SCRIPT_DIR}")"
cd "${ROOT_DIR}"

BASE_URL="${BASE_URL:-http://localhost:8080}"
COLLECTION_PATH="${COLLECTION_PATH:-postman/e2e/CooBS_End_To_End.postman_collection.json}"
ENVIRONMENT_PATH="${ENVIRONMENT_PATH:-postman/CooBS_Local.postman_environment.json}"
OUTPUT_DIR="${OUTPUT_DIR:-test-results/postman}"
SKIP_HEALTH_CHECK="${SKIP_HEALTH_CHECK:-false}"

echo "================================================================="
echo " CooBS Platform - Postman Newman E2E Test Suite Execution        "
echo "================================================================="
echo "Target Gateway URL  : ${BASE_URL}"
echo "Collection File     : ${COLLECTION_PATH}"
echo "Environment File    : ${ENVIRONMENT_PATH}"
echo "Report Output Dir   : ${OUTPUT_DIR}"
echo ""

if [ ! -f "${COLLECTION_PATH}" ]; then
    echo "ERROR: Collection not found at ${COLLECTION_PATH}"
    exit 1
fi
if [ ! -f "${ENVIRONMENT_PATH}" ]; then
    echo "ERROR: Environment not found at ${ENVIRONMENT_PATH}"
    exit 1
fi

mkdir -p "${OUTPUT_DIR}"
JUNIT_REPORT="${OUTPUT_DIR}/postman-e2e-junit.xml"

# Health Check
if [ "${SKIP_HEALTH_CHECK}" != "true" ]; then
    echo "[1/3] Checking API Gateway connectivity..."
    if curl -sf "${BASE_URL}/actuator/health" | grep -q "UP"; then
        echo "  [PASS] API Gateway is healthy (status: UP)"
    else
        echo "  [WARN] Could not verify API Gateway UP status at ${BASE_URL}"
        echo "         Proceeding with Newman execution..."
    fi
fi

echo ""
echo "[2/3] Resolving Newman execution runtime..."
RUNNER="none"
if command -v newman >/dev/null 2>&1; then
    RUNNER="newman"
    echo "  [INFO] Using global newman: $(command -v newman)"
elif command -v npx >/dev/null 2>&1; then
    RUNNER="npx"
    echo "  [INFO] Using npx newman runner"
elif command -v docker >/dev/null 2>&1; then
    RUNNER="docker"
    echo "  [INFO] Using Docker container runner (postman/newman:alpine)"
else
    echo "ERROR: Neither newman, npx, nor docker is available."
    exit 1
fi

echo ""
echo "[3/3] Executing Postman End-to-End Test Suite..."
set +e
if [ "${RUNNER}" = "newman" ]; then
    newman run "${COLLECTION_PATH}" \
        -e "${ENVIRONMENT_PATH}" \
        --env-var "baseUrl=${BASE_URL}" \
        --reporters cli,junit \
        --reporter-junit-export "${JUNIT_REPORT}" \
        --bail false
    EXIT_CODE=$?
elif [ "${RUNNER}" = "npx" ]; then
    npx -y newman run "${COLLECTION_PATH}" \
        -e "${ENVIRONMENT_PATH}" \
        --env-var "baseUrl=${BASE_URL}" \
        --reporters cli,junit \
        --reporter-junit-export "${JUNIT_REPORT}" \
        --bail false
    EXIT_CODE=$?
elif [ "${RUNNER}" = "docker" ]; then
    docker run --rm --network host \
        -v "${ROOT_DIR}:/workspace" \
        -w /workspace \
        postman/newman:alpine run "${COLLECTION_PATH}" \
        -e "${ENVIRONMENT_PATH}" \
        --env-var "baseUrl=${BASE_URL}" \
        --reporters cli,junit \
        --reporter-junit-export "${JUNIT_REPORT}" \
        --bail false
    EXIT_CODE=$?
fi
set -e

echo ""
echo "================================================================="
if [ "${EXIT_CODE}" -eq 0 ]; then
    echo " POSTMAN E2E RUN PASSED"
    echo " JUnit Report: ${JUNIT_REPORT}"
else
    echo " POSTMAN E2E RUN FAILED with exit code ${EXIT_CODE}"
    echo " Review report at: ${JUNIT_REPORT}"
fi
echo "================================================================="

exit "${EXIT_CODE}"
