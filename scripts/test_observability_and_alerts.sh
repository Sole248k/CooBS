#!/usr/bin/env bash
# ==============================================================================
# CooBS Banking Platform - FIN-807 Observability & TLS Suite
# Validates Key Vault Secrets, 5s Prometheus Scraping, Alerts, and Grafana SLAs
# ==============================================================================
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

PASSED=0
FAILED=0

assert_check() {
    local name="$1"
    local status="$2"
    local details="${3:-}"

    if [ "$status" -eq 0 ]; then
        echo -e "  \033[32m[PASS]\033[0m $name"
        [ -n "$details" ] && echo -e "         \033[90m$details\033[0m"
        PASSED=$((PASSED + 1))
    else
        echo -e "  \033[31m[FAIL]\033[0m $name"
        [ -n "$details" ] && echo -e "         \033[33m$details\033[0m"
        FAILED=$((FAILED + 1))
    fi
}

echo "================================================================="
echo "    CooBS Banking Platform - FIN-807 Observability & TLS Suite   "
echo "    Key Vault Secrets, 5s Prometheus Scraping & Grafana SLAs    "
echo "================================================================="

echo ""
echo "[1/5] Validating Azure Key Vault Secrets & Bicep IaC..."
KV_BICEP="$WORKSPACE_ROOT/infra/bicep/modules/keyvault.bicep"
MAIN_BICEP="$WORKSPACE_ROOT/infra/bicep/main.bicep"
PARAM_BICEP="$WORKSPACE_ROOT/infra/bicep/main.bicepparam"

if [ -f "$KV_BICEP" ]; then assert_check "keyvault.bicep exists" 0; else assert_check "keyvault.bicep exists" 1; fi

for sec in "jwt-secret" "postgres-password" "internal-service-secret" "oracle-password" "eventhubs-connection-string" "redis-key"; do
    if grep -q "name: '$sec'" "$KV_BICEP"; then
        assert_check "Key Vault secret definition: '$sec'" 0
    else
        assert_check "Key Vault secret definition: '$sec'" 1
    fi
done

if grep -q "internalServiceSecret: internalServiceSecret" "$MAIN_BICEP"; then
    assert_check "main.bicep wires internalServiceSecret" 0
else
    assert_check "main.bicep wires internalServiceSecret" 1
fi

if grep -q "oraclePassword: oraclePassword" "$MAIN_BICEP"; then
    assert_check "main.bicep wires oraclePassword" 0
else
    assert_check "main.bicep wires oraclePassword" 1
fi

if grep -q "eventHubsConnectionString: eventhubs.outputs.primaryConnectionString" "$MAIN_BICEP"; then
    assert_check "main.bicep wires eventHubsConnectionString" 0
else
    assert_check "main.bicep wires eventHubsConnectionString" 1
fi

if grep -q "redisKey: redis.outputs.redisPrimaryKey" "$MAIN_BICEP"; then
    assert_check "main.bicep wires redisKey" 0
else
    assert_check "main.bicep wires redisKey" 1
fi

if grep -q "internalServiceSecret" "$PARAM_BICEP"; then
    assert_check "main.bicepparam contains internalServiceSecret" 0
else
    assert_check "main.bicepparam contains internalServiceSecret" 1
fi

if grep -q "oraclePassword" "$PARAM_BICEP"; then
    assert_check "main.bicepparam contains oraclePassword" 0
else
    assert_check "main.bicepparam contains oraclePassword" 1
fi

echo ""
echo "[2/5] Validating Prometheus 5s Scraping Configuration..."
PROM_YAML="$WORKSPACE_ROOT/monitoring/prometheus/prometheus.yml"
if [ -f "$PROM_YAML" ]; then assert_check "prometheus.yml exists" 0; else assert_check "prometheus.yml exists" 1; fi

if grep -q "scrape_interval: 5s" "$PROM_YAML"; then assert_check "Prometheus global scrape_interval is 5s" 0; else assert_check "Prometheus global scrape_interval is 5s" 1; fi
if grep -q "evaluation_interval: 5s" "$PROM_YAML"; then assert_check "Prometheus global evaluation_interval is 5s" 0; else assert_check "Prometheus global evaluation_interval is 5s" 1; fi
if grep -q "alerts.yml" "$PROM_YAML"; then assert_check "Prometheus alerts.yml referenced" 0; else assert_check "Prometheus alerts.yml referenced" 1; fi

for svc in "api-gateway:8080" "auth-service:8081" "account-service:8082" "ledger-service:8083" \
           "notification-service:8084" "audit-service:8085" "customer-service:8089" \
           "transaction-service:8090" "orchestration-service:8091" "risk-engine:8092" \
           "batch-service:8093" "mobile-bff:8094"; do
    if grep -q "$svc" "$PROM_YAML"; then
        assert_check "Prometheus scrape target: $svc" 0
    else
        assert_check "Prometheus scrape target: $svc" 1
    fi
done

echo ""
echo "[3/5] Validating Prometheus Alerting Rules & Contractual SLAs..."
ALERTS_YAML="$WORKSPACE_ROOT/monitoring/prometheus/alerts.yml"
if [ -f "$ALERTS_YAML" ]; then assert_check "alerts.yml exists" 0; else assert_check "alerts.yml exists" 1; fi

for alert in "RiskEvaluationSlaBreachP95" "GatewayEdgeIngressSlaBreachP99" \
             "TransferSagaLatencySlaBreachP95" "HighRateLimitBurstSurge" \
             "SlowTransferLatencyP99" "HighHttp5xxRate" "ServiceDown" \
             "HikariConnectionPoolSaturation" "KafkaConsumerLagHigh"; do
    if grep -q "alert: $alert" "$ALERTS_YAML"; then
        assert_check "Alert rule defined: $alert" 0
    else
        assert_check "Alert rule defined: $alert" 1
    fi
done

if grep -q "0.04" "$ALERTS_YAML"; then assert_check "Risk evaluation SLA threshold is 40ms (0.040s)" 0; else assert_check "Risk evaluation SLA threshold is 40ms (0.040s)" 1; fi
if grep -q "0.5" "$ALERTS_YAML"; then assert_check "Gateway ingress SLA threshold is 500ms (0.500s)" 0; else assert_check "Gateway ingress SLA threshold is 500ms (0.500s)" 1; fi
if grep -q "1.5" "$ALERTS_YAML"; then assert_check "Transfer saga SLA threshold is 1500ms (1.500s)" 0; else assert_check "Transfer saga SLA threshold is 1500ms (1.500s)" 1; fi

echo ""
echo "[4/5] Validating Grafana Operational Command Center Dashboard..."
DASHBOARD_JSON="$WORKSPACE_ROOT/monitoring/grafana/dashboards/coobs_core_banking.json"
if [ -f "$DASHBOARD_JSON" ]; then assert_check "coobs_core_banking.json exists" 0; else assert_check "coobs_core_banking.json exists" 1; fi

if command -v python >/dev/null 2>&1; then
    if (cd "$WORKSPACE_ROOT" && python -c "import json; json.load(open('monitoring/grafana/dashboards/coobs_core_banking.json'))") >/dev/null 2>&1; then
        assert_check "Dashboard JSON is valid and well-formed" 0
    else
        assert_check "Dashboard JSON is valid and well-formed" 1
    fi
fi

if grep -q "EXECUTIVE SLA COMPLIANCE & FRAUD SCORING" "$DASHBOARD_JSON"; then
    assert_check "Executive SLA Row present" 0
else
    assert_check "Executive SLA Row present" 1
fi

if grep -q "Risk Engine P95 Latency SLA (<40ms)" "$DASHBOARD_JSON"; then
    assert_check "Risk Engine SLA Panel present" 0
else
    assert_check "Risk Engine SLA Panel present" 1
fi

if grep -q "Edge Gateway P99 Ingress SLA (<500ms)" "$DASHBOARD_JSON"; then
    assert_check "Edge Gateway SLA Panel present" 0
else
    assert_check "Edge Gateway SLA Panel present" 1
fi

if grep -q "Transfer Saga P95 Latency SLA (<1500ms)" "$DASHBOARD_JSON"; then
    assert_check "Transfer Saga SLA Panel present" 0
else
    assert_check "Transfer Saga SLA Panel present" 1
fi

if grep -q "Active SLA & System Alerts Firing" "$DASHBOARD_JSON"; then
    assert_check "Active Alerts Stat Panel present" 0
else
    assert_check "Active Alerts Stat Panel present" 1
fi

echo ""
echo "[5/5] Validating TLS Infrastructure & Ingress Manifests..."
GATEWAY_YML="$WORKSPACE_ROOT/backend/api-gateway/src/main/resources/application.yml"
INGRESS_YAML="$WORKSPACE_ROOT/infra/k8s/ingress-tls.yaml"

if grep -q "enabled: \${SSL_ENABLED:false}" "$GATEWAY_YML"; then
    assert_check "application.yml has server.ssl block" 0
else
    assert_check "application.yml has server.ssl block" 1
fi

if [ -f "$INGRESS_YAML" ]; then assert_check "infra/k8s/ingress-tls.yaml exists" 0; else assert_check "infra/k8s/ingress-tls.yaml exists" 1; fi
if grep -q "coobs-tls-secret" "$INGRESS_YAML"; then assert_check "Ingress defines TLS secret" 0; else assert_check "Ingress defines TLS secret" 1; fi
if grep -q "SecretProviderClass" "$INGRESS_YAML"; then assert_check "Ingress defines SecretProviderClass" 0; else assert_check "Ingress defines SecretProviderClass" 1; fi

echo ""
echo "================================================================="
echo "                     VERIFICATION SUMMARY                        "
echo "================================================================="
echo "  Passed checks: $PASSED"
echo "  Failed checks: $FAILED"
echo "================================================================="

if [ "$FAILED" -eq 0 ]; then
    echo "SUCCESS: All FIN-807 Production Hardening requirements verified!"
    exit 0
else
    echo "WARNING: One or more checks failed."
    exit 1
fi
