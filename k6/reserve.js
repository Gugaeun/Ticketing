import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

// 사용법: k6 run -e BASE_URL=http://localhost:8080 k6/reserve.js
// setup 에서 관리자로 공연/회차(재고 STOCK)를 만들고 USERS 명을 가입·로그인시킨 뒤,
// 본 시나리오에서 모든 VU 가 동시에 1회씩 예매한다. (BCrypt 비용이 측정에 섞이지 않도록 분리)
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = Number(__ENV.USERS || 1000);
const STOCK = Number(__ENV.STOCK || 100);
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@ticketing.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin1234';

const reserveDuration = new Trend('reserve_duration', true);
const json = { headers: { 'Content-Type': 'application/json' } };

export const options = {
  setupTimeout: '10m',
  scenarios: {
    burst: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '2m' },
  },
  thresholds: { reserve_duration: ['p(95)<5000'] },
};

function login(email, password) {
  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password }), json);
  return res.json('accessToken');
}

export function setup() {
  const run = Date.now();
  const adminAuth = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${login(ADMIN_EMAIL, ADMIN_PASSWORD)}` } };

  const perfId = http.post(`${BASE_URL}/api/admin/performances`,
    JSON.stringify({ title: `bench-${run}`, venue: 'bench', description: '' }), adminAuth).json();
  const open = new Date(Date.now() - 60000).toISOString().slice(0, 19);
  const start = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 19);
  const scheduleId = http.post(`${BASE_URL}/api/admin/performances/${perfId}/schedules`,
    JSON.stringify({ startAt: start, ticketOpenAt: open, totalStock: STOCK }), adminAuth).json();

  const emails = Array.from({ length: USERS }, (_, i) => `bench${run}_${i}@test.com`);
  const tokens = [];
  for (let i = 0; i < USERS; i += 50) {
    const chunk = emails.slice(i, i + 50);
    http.batch(chunk.map((email) => ['POST', `${BASE_URL}/api/auth/signup`,
      JSON.stringify({ email, password: 'password123', name: 'bench' }), json]));
    http.batch(chunk.map((email) => ['POST', `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password: 'password123' }), json]))
      .forEach((r) => tokens.push(r.json('accessToken')));
  }
  return { scheduleId, tokens };
}

export default function (data) {
  const res = http.post(`${BASE_URL}/api/reservations`,
    JSON.stringify({ scheduleId: data.scheduleId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.tokens[__VU - 1]}` } });
  reserveDuration.add(res.timings.duration);
  check(res, { 'ok or sold out': (r) => r.status === 200 || r.status === 409 });
}
