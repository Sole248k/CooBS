import http from 'k6/http';
import { check, sleep } from 'k6';

// ---------------------------------------------------------------------------
// CooBS & PureSilver Cloud Load Test Benchmark (k6)
// Corresponds to FIN-807 Production SLAs (Gateway P99 < 500ms, Saga P95 < 1500ms)
// ---------------------------------------------------------------------------

export const options = {
  stages: [
    { duration: '30s', target: 20 },  // Ramp-up to 20 virtual users
    { duration: '2m', target: 50 },   // Sustained load with 50 users
    { duration: '30s', target: 100 }, // Spike test up to 100 users
    { duration: '30s', target: 0 },   // Graceful cool-down
  ],
  thresholds: {
    // Assert against CooBS Contractual SLAs
    'http_req_duration{endpoint:health}': ['p(99)<500'],
    'http_req_duration{endpoint:shop}': ['p(95)<500'],
    'http_req_duration{endpoint:auth}': ['p(95)<1000'],
    http_req_failed: ['rate<0.01'], // Global failure rate below 1%
  },
};

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const STAFF_PASS = __ENV.STAFF_PASSWORD || 'StaffPass123!';

export default function () {
  const jsonHeaders = {
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
  };

  // 1. Health check & Edge Ingress
  const healthRes = http.get(`${BASE_URL}/actuator/health`, {
    ...jsonHeaders,
    tags: { endpoint: 'health' },
  });
  check(healthRes, {
    'health check status is 200': (r) => r.status === 200,
  });

  sleep(1);

  // 2. PureSilver Grocery Partner Catalogue
  const shopRes = http.get(`${BASE_URL}/api/v1/shop/products`, {
    ...jsonHeaders,
    tags: { endpoint: 'shop' },
  });
  check(shopRes, {
    'shop products status is 200': (r) => r.status === 200,
    'shop products returned array': (r) => Array.isArray(r.json()),
  });

  sleep(1);

  // 3. Staff Auth Login
  const loginPayload = JSON.stringify({
    username: 'teller1',
    password: STAFF_PASS,
  });

  const authRes = http.post(`${BASE_URL}/api/v1/auth/staff/login`, loginPayload, {
    ...jsonHeaders,
    tags: { endpoint: 'auth' },
  });

  check(authRes, {
    'login status is 200 or 429 (rate-limited)': (r) => r.status === 200 || r.status === 429,
  });

  if (authRes.status === 200 && authRes.json('token')) {
    const token = authRes.json('token');
    const authHeaders = {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    };

    // 4. Query Member Accounts
    const acctsRes = http.get(`${BASE_URL}/api/v1/customers/1000/accounts`, authHeaders);
    check(acctsRes, {
      'customer accounts status is 200': (r) => r.status === 200,
    });
  }

  sleep(2);
}
