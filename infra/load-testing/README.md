# CooBS Cloud Load Testing & SLA Verification

This directory provides automated load testing for the CooBS enterprise banking platform and PureSilver retail depot services deployed in Azure.

## Available Runners

### 1. Azure Load Testing (Cloud-Distributed Engines)
Runs from managed Microsoft Azure test engines, avoiding local bandwidth limitations.

- **Test Plan:** [coobs-loadtest.jmx](file:///d:/Fullstack/Capstone-dev/infra/load-testing/coobs-loadtest.jmx) (Apache JMeter plan)
- **Configuration & Failure Criteria:** [loadtest-config.yaml](file:///d:/Fullstack/Capstone-dev/infra/load-testing/loadtest-config.yaml)
- **Automated Runner:** [run-cloud-loadtest.ps1](file:///d:/Fullstack/Capstone-dev/infra/enterprise/run-cloud-loadtest.ps1)

#### Running Azure Load Test:
```powershell
# Default: 75 virtual users for 180 seconds against discovered Azure Front Door/Ingress
.\infra\enterprise\run-cloud-loadtest.ps1

# Custom duration & higher concurrency
.\infra\enterprise\run-cloud-loadtest.ps1 -DurationSec 300 -TotalVUs 150
```

---

### 2. Local / CI k6 Runner
Lightweight alternative that runs from your workstation terminal or CI/CD pipelines.

- **Script:** [tests/load/k6-benchmark.js](file:///d:/Fullstack/Capstone-dev/tests/load/k6-benchmark.js)

#### Running with k6:
```powershell
# Run against local dev
k6 run tests/load/k6-benchmark.js

# Run against Azure deployment
k6 run -e GATEWAY_URL="https://<your-frontdoor-hostname>" tests/load/k6-benchmark.js
```

---

## Contractual SLA Thresholds (FIN-807)

The test plans evaluate the system against the SLA rules defined in `monitoring/prometheus/alerts.yml`:

| Metric | Target SLA | Test Behavior |
| :--- | :--- | :--- |
| **Gateway Edge Ingress (P99)** | `< 500 ms` | Fails if edge proxy latency breaches 500ms |
| **Risk Engine Evaluation (P95)** | `< 40 ms` | Monitored in Grafana during transfer sagas |
| **Transfer Saga Latency (P95)** | `< 1,500 ms` | Fails if 2-phase ledger sagas exceed 1.5s |
| **HTTP Error Rate** | `< 1.0 %` | Fails if 5xx server errors surge |
| **Rate Limiter (HTTP 429)** | Graceful throttle | Auth route throttles spikes above 10 req/s |
