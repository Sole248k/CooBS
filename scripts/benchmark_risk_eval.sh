#!/usr/bin/env bash
# ==============================================================================
# benchmark_risk_eval.sh: Benchmark Risk Evaluation Latency SLA (FIN-805)
# Validates Handshake H5 / Endpoint R1 SLA: p95 latency < 40ms
# Target: POST /internal/v1/risk/evaluate (Port 8092)
# Reference: integrated-capstone-plans/DAY_BY_DAY_PARALLEL_EXECUTION_ROADMAP.md (Day 5)
# ==============================================================================
set -euo pipefail

RISK_ENGINE_URL="${1:-http://localhost:8092}"
ITERATIONS="${2:-100}"
WARMUP_REQUESTS="${3:-10}"
SLA_P95_MS="40.0"
INTERNAL_SECRET="${INTERNAL_SECRET:-coobs-risk-internal-secret-2026}"
JWT_SECRET="${JWT_SECRET:-404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}"

echo "================================================================="
echo " CooBS Platform - Risk Evaluation SLA Latency Benchmark (FIN-805) "
echo " Target SLA: p95 < ${SLA_P95_MS} ms (Endpoint: POST /internal/v1/risk/evaluate)"
echo "================================================================="

# Execute benchmark using python3 / python standard library
PYTHON_BIN=""
if command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
  PYTHON_BIN="python"
else
  echo "ERROR: python3 or python is required to run the SLA benchmark." >&2
  exit 1
fi

"$PYTHON_BIN" - "$RISK_ENGINE_URL" "$ITERATIONS" "$WARMUP_REQUESTS" "$SLA_P95_MS" "$INTERNAL_SECRET" "$JWT_SECRET" << 'EOF'
import sys
import os
import time
import json
import uuid
import hmac
import hashlib
import base64
import urllib.parse
import http.client
import math

risk_engine_url = sys.argv[1]
iterations = int(sys.argv[2])
warmup_requests = int(sys.argv[3])
sla_threshold_ms = float(sys.argv[4])
internal_secret = sys.argv[5]
jwt_secret = sys.argv[6]

parsed = urllib.parse.urlparse(risk_engine_url)
host = parsed.hostname or "127.0.0.1"
if host == "localhost":
    host = "127.0.0.1"
port = parsed.port or (443 if parsed.scheme == "https" else 80)

# 1. Healthcheck Probe
print(f"\n[1/5] Probing risk-engine health ({risk_engine_url}/actuator/health)...")
try:
    if parsed.scheme == "https":
        conn = http.client.HTTPSConnection(host, port, timeout=5)
    else:
        conn = http.client.HTTPConnection(host, port, timeout=5)
    conn.request("GET", "/actuator/health")
    resp = conn.getresponse()
    resp_body = resp.read()
    if resp.status == 200:
        print("  [PASS] risk-engine is UP and healthy (HTTP 200)")
    else:
        print(f"  [WARN] Unexpected health status code: {resp.status}")
except Exception as e:
    print(f"ERROR: Failed to connect to risk-engine at {risk_engine_url}: {e}", file=sys.stderr)
    sys.exit(1)

# 2. Token generation & auth handshake
print("\n[2/5] Establishing internal service credentials...")
try:
    key_bytes = base64.b64decode(jwt_secret)
except Exception:
    key_bytes = jwt_secret.encode("utf-8")

if len(key_bytes) < 32:
    key_bytes = key_bytes.ljust(32, b"\0")

header_json = json.dumps({"alg": "HS256", "typ": "JWT"}).encode("utf-8")
now = int(time.time())
payload_json = json.dumps({
    "sub": "service-internal",
    "userId": 0,
    "roles": ["ROLE_SERVICE"],
    "iat": now,
    "exp": now + 86400
}).encode("utf-8")

def b64url(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode("utf-8")

h_b64 = b64url(header_json)
p_b64 = b64url(payload_json)
to_sign = f"{h_b64}.{p_b64}".encode("utf-8")
sig = b64url(hmac.new(key_bytes, to_sign, hashlib.sha256).digest())
token = f"{h_b64}.{p_b64}.{sig}"

headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {token}",
    "X-Internal-Secret": internal_secret
}

# Auth handshake probe
probe_body = json.dumps({
    "transactionId": str(uuid.uuid4()),
    "customerId": 12,
    "amount": 100.0,
    "currency": "PHP"
})
conn.request("POST", "/internal/v1/risk/evaluate", probe_body, headers)
probe_res = conn.getresponse()
probe_res.read()
if probe_res.status == 200:
    print("  [PASS] Authenticated successfully with internal service credentials (ROLE_SERVICE)")
else:
    print(f"ERROR: Auth probe returned HTTP status: {probe_res.status}", file=sys.stderr)
    sys.exit(1)

# 3. Warm-up
print(f"\n[3/5] Warming up JVM JIT compiler and Redis connection pool ({warmup_requests} requests)...")
for w in range(warmup_requests):
    w_body = json.dumps({
        "transactionId": str(uuid.uuid4()),
        "customerId": 2000 + (w % 5),
        "sourceAccountId": "ACC_10000012",
        "destinationAccountId": "ACC_10000016",
        "amount": 150.0,
        "currency": "PHP",
        "channel": "APP",
        "kycTier": "TIER_2_STANDARD",
        "dailyLimit": 50000.0,
        "isNewBeneficiary": False
    })
    conn.request("POST", "/internal/v1/risk/evaluate", w_body, headers)
    r = conn.getresponse()
    r.read()
print("  [PASS] Warm-up complete. Connection pool & execution paths primed.")

# 4. Benchmark execution
print(f"\n[4/5] Executing latency benchmark ({iterations} iterations)...")
client_latencies = []
server_latencies = []
decisions = {"APPROVE": 0, "DECLINE": 0}
failed_requests = 0

for i in range(1, iterations + 1):
    cust_id = 3000 + (i % 25)
    is_new = (i % 10 == 0)
    amount = 75000.0 if (i % 20 == 0) else round(100.0 + (i * 7.5 % 900), 2)
    
    body = json.dumps({
        "transactionId": str(uuid.uuid4()),
        "customerId": cust_id,
        "sourceAccountId": f"ACC_{cust_id}",
        "destinationAccountId": "ACC_99999999",
        "amount": amount,
        "currency": "PHP",
        "channel": "APP",
        "kycTier": "TIER_2_STANDARD",
        "dailyLimit": 50000.0,
        "isNewBeneficiary": is_new
    })

    t0 = time.perf_counter()
    try:
        conn.request("POST", "/internal/v1/risk/evaluate", body, headers)
        resp = conn.getresponse()
        raw = resp.read().decode("utf-8")
        t1 = time.perf_counter()

        elapsed_ms = round((t1 - t0) * 1000, 2)

        if resp.status == 200:
            client_latencies.append(elapsed_ms)
            data = json.loads(raw)
            if "evaluatedInMs" in data:
                server_latencies.append(float(data["evaluatedInMs"]))
            dec = data.get("decision", "UNKNOWN")
            decisions[dec] = decisions.get(dec, 0) + 1
        else:
            failed_requests += 1
            print(f"  [FAIL] Request {i} returned status: {resp.status}")
    except Exception as e:
        failed_requests += 1
        print(f"  [FAIL] Request {i} error: {e}")

    if i % 25 == 0 or i == iterations:
        print(f"  Progress: {i} / {iterations} completed...")

conn.close()

# 5. Percentiles and SLA
print("\n[5/5] Computing latency percentiles & SLA compliance...")

def get_percentile(lst, p):
    if not lst:
        return 0.0
    sorted_lst = sorted(lst)
    idx = math.ceil((p / 100.0) * len(sorted_lst)) - 1
    idx = max(0, min(idx, len(sorted_lst) - 1))
    return round(sorted_lst[idx], 2)

c_min = min(client_latencies) if client_latencies else 0.0
c_max = max(client_latencies) if client_latencies else 0.0
c_avg = round(sum(client_latencies) / len(client_latencies), 2) if client_latencies else 0.0
c_p50 = get_percentile(client_latencies, 50)
c_p90 = get_percentile(client_latencies, 90)
c_p95 = get_percentile(client_latencies, 95)
c_p99 = get_percentile(client_latencies, 99)

s_min = min(server_latencies) if server_latencies else 0.0
s_max = max(server_latencies) if server_latencies else 0.0
s_avg = round(sum(server_latencies) / len(server_latencies), 2) if server_latencies else 0.0
s_p50 = get_percentile(server_latencies, 50)
s_p90 = get_percentile(server_latencies, 90)
s_p95 = get_percentile(server_latencies, 95)
s_p99 = get_percentile(server_latencies, 99)

print("\n" + "=" * 65)
print(f"            BENCHMARK RESULTS SUMMARY (Sample Size: {iterations})")
print("=" * 65)
print(f"{'Metric':<18} | {'Client Round-Trip':<18} | {'Server Evaluator':<18}")
print("-" * 19 + "+" + "-" * 20 + "+" + "-" * 19)
print(f"{'Minimum (p0)':<18} | {str(c_min) + ' ms':<18} | {str(s_min) + ' ms':<18}")
print(f"{'Median (p50)':<18} | {str(c_p50) + ' ms':<18} | {str(s_p50) + ' ms':<18}")
print(f"{'Average (mean)':<18} | {str(c_avg) + ' ms':<18} | {str(s_avg) + ' ms':<18}")
print(f"{'90th %-tile (p90)':<18} | {str(c_p90) + ' ms':<18} | {str(s_p90) + ' ms':<18}")
print(f"{'95th %-tile (p95)':<18} | {str(c_p95) + ' ms':<18} | {str(s_p95) + ' ms':<18}")
print(f"{'99th %-tile (p99)':<18} | {str(c_p99) + ' ms':<18} | {str(s_p99) + ' ms':<18}")
print(f"{'Maximum (p100)':<18} | {str(c_max) + ' ms':<18} | {str(s_max) + ' ms':<18}")
print("-" * 19 + "+" + "-" * 20 + "+" + "-" * 19)
print(f"Decisions Recorded : APPROVE: {decisions.get('APPROVE', 0)}, DECLINE: {decisions.get('DECLINE', 0)}")
err_rate = round((failed_requests / iterations) * 100, 2)
print(f"Failed Requests    : {failed_requests} (Error Rate: {err_rate}%)")
print("=" * 65)

print("\nSLA VERIFICATION:")
print(f"  Target SLA Threshold : p95 < {sla_threshold_ms} ms")
print(f"  Observed Client p95  : {c_p95} ms")
print(f"  Observed Server p95  : {s_p95} ms")

if c_p95 <= sla_threshold_ms and failed_requests == 0:
    print("\n" + "=" * 65)
    print(" [SUCCESS] Risk Evaluation SLA Benchmark Verified (FIN-805)       ")
    print(f"           Observed p95 ({c_p95} ms) comfortably met < {sla_threshold_ms} ms SLA! ")
    print("=" * 65)
    sys.exit(0)
else:
    print("\n" + "=" * 65)
    print(" [FAILURE] Risk Evaluation SLA Threshold Breached or Errors Detected ")
    print("=" * 65)
    sys.exit(1)
EOF
