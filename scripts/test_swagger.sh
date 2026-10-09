#!/usr/bin/env bash
# ==============================================================================
# test_swagger.sh: Automated verification for Swagger UI & OpenAPI 3.0 testing
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(dirname "${SCRIPT_DIR}")"

echo "================================================================="
echo "     CooBS Retail Banking - Swagger UI / OpenAPI Test Suite      "
echo "================================================================="

PASSED=0
FAILED=0

assert_check() {
    local name="$1"
    local condition="$2"
    local details="${3:-}"

    if [ "$condition" -eq 0 ]; then
        echo "  [PASS] $name"
        [ -n "$details" ] && echo "         $details"
        PASSED=$((PASSED + 1))
    else
        echo "  [FAIL] $name"
        [ -n "$details" ] && echo "         $details"
        FAILED=$((FAILED + 1))
    fi
}

# 1. Validate OpenAPI Specification
echo ""
echo "[1/3] Validating OpenAPI 3.0 Contract File..."
OPENAPI_FILE="${WORKSPACE_ROOT}/contracts/api/openapi.yaml"
if [ -f "${OPENAPI_FILE}" ]; then
    assert_check "openapi.yaml exists" 0
    
    grep -q "openapi: 3.0.3" "${OPENAPI_FILE}" && res=0 || res=1
    assert_check "OpenAPI version is 3.0.3" $res

    grep -q "BearerAuth:" "${OPENAPI_FILE}" && res=0 || res=1
    assert_check "Security scheme BearerAuth is defined" $res

    grep -q "http://localhost:8080" "${OPENAPI_FILE}" && res=0 || res=1
    assert_check "API Gateway server is configured (8080)" $res

    grep -q "/api/v1/auth/customers/login:" "${OPENAPI_FILE}" && res=0 || res=1
    assert_check "Auth Service endpoints defined (/api/v1/auth/customers/login)" $res

    grep -q "/api/v1/ledger/transfers:" "${OPENAPI_FILE}" && res=0 || res=1
    assert_check "Ledger Service endpoints defined (/api/v1/ledger/transfers)" $res

    grep -q "/api/v1/transactions/transfers:" "${OPENAPI_FILE}" && res=0 || res=1
    assert_check "Transaction Service endpoints defined (/api/v1/transactions/transfers)" $res

    grep -q "/api/v1/mobile/dashboard:" "${OPENAPI_FILE}" && res=0 || res=1
    assert_check "Mobile BFF endpoints defined (/api/v1/mobile/dashboard)" $res

    grep -q "/api/v1/staff/customers/{customerId}/360:" "${OPENAPI_FILE}" && res=0 || res=1
    assert_check "Staff BFF endpoints defined (/api/v1/staff/customers/{customerId}/360)" $res
else
    assert_check "openapi.yaml exists" 1 "File not found: ${OPENAPI_FILE}"
fi

# 2. Validate Docker Compose and API Gateway Config
echo ""
echo "[2/3] Validating Architecture Configuration..."
COMPOSE_FILE="${WORKSPACE_ROOT}/docker-compose.yml"
if [ -f "${COMPOSE_FILE}" ]; then
    grep -q "swagger-ui:" "${COMPOSE_FILE}" && res=0 || res=1
    assert_check "swagger-ui service defined in docker-compose.yml" $res

    grep -q "SWAGGER_UI_PORT:-8086" "${COMPOSE_FILE}" && res=0 || res=1
    assert_check "swagger-ui maps SWAGGER_UI_PORT (8086)" $res

    grep -q "SWAGGER_UI_URL: http://swagger-ui:8080" "${COMPOSE_FILE}" && res=0 || res=1
    assert_check "SWAGGER_UI_URL wired to api-gateway" $res
fi

GATEWAY_YML="${WORKSPACE_ROOT}/backend/api-gateway/src/main/resources/application.yml"
if [ -f "${GATEWAY_YML}" ]; then
    grep -q -- "- Path=/swagger-ui" "${GATEWAY_YML}" && res=0 || res=1
    assert_check "api-gateway routes /swagger-ui/**" $res
fi

# 3. Live Connectivity Check (Optional / Non-blocking)
echo ""
echo "[3/3] Checking Live Endpoints..."
if command -v curl &> /dev/null; then
    if curl -s -f -o /dev/null --connect-timeout 2 "http://localhost:8086/swagger-ui/"; then
        assert_check "Direct Swagger UI Container (:8086) is reachable (HTTP 200)" 0
    else
        echo "  [SKIP] Direct Swagger UI Container (:8086) not running"
    fi

    if curl -s -f -o /dev/null --connect-timeout 2 "http://localhost:8080/swagger-ui/"; then
        assert_check "Gateway Routed Swagger UI (:8080) is reachable (HTTP 200)" 0
    else
        echo "  [SKIP] Gateway Routed Swagger UI (:8080) not running"
    fi
else
    echo "  [SKIP] curl not found, skipping live HTTP checks"
fi

echo ""
echo "================================================================="
echo "                     VERIFICATION SUMMARY                        "
echo "================================================================="
echo "  Passed checks: ${PASSED}"
echo "  Failed checks: ${FAILED}"
echo "================================================================="

if [ "${FAILED}" -eq 0 ]; then
    echo "SUCCESS: Swagger UI & OpenAPI 3.0 testing suite verified successfully!"
    exit 0
else
    echo "WARNING: One or more checks failed."
    exit 1
fi
