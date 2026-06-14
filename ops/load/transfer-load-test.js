// k6 load test (Phase 6). Run: k6 run ops/load/transfer-load-test.js
// Drives the read path (GET /balance, open by default). A secured POST /api/v1/transfers
// scenario is included below and can be enabled with a Bearer token.
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(99)<150'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:33400';

export default function () {
  const userId = Math.floor(Math.random() * 12) + 1;
  const res = http.get(`${BASE}/balance?userId=${userId}`);
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(0.1);
}

// --- Authenticated write scenario (enable by exporting a function + JWT) ---
// const TOKEN = __ENV.JWT;
// export function initiateTransfer() {
//   const body = JSON.stringify({ senderId: 1, recipientId: 2, amount: 10.0 });
//   const params = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN}` } };
//   const res = http.post(`${BASE}/api/v1/transfers`, body, params);
//   check(res, { 'created or ok': (r) => r.status === 201 || r.status === 200 });
// }
