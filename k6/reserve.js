import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// 사용법 (프로젝트 폴더에서):
//   k6 run -e USERS=1000 -e STOCK=100 k6/reserve.js
// 앱은 측정할 방식으로 띄워 둔다: --reservation.strategy=none | pessimistic | optimistic
//
// setup 에서 관리자로 공연/회차(재고 STOCK)를 만들고 USERS 명을 가입·로그인시킨 뒤,
// 본 시나리오에서 모든 VU 가 동시에 1회씩 예매한다. (BCrypt 비용이 측정에 섞이지 않도록 분리)
// teardown 에서 정합성(초과 판매)을 출력하고 임시 공연을 지운다. (가입한 bench 회원은 남으므로 README 의 정리 SQL 로 지운다)
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = Number(__ENV.USERS || 1000);
const STOCK = Number(__ENV.STOCK || 100);
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@ticketing.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin1234';

// 200(예매 성공)과 409(재고 소진/충돌 한도 초과)는 정상 응답으로 본다. 그 외만 실패다.
// (회원가입은 201, 이미 가입된 bench 회원은 409 이므로 같이 정상으로 본다)
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const reserveDuration = new Trend('reserve_duration', true);
const startTs = new Trend('reserve_start_ts');
const endTs = new Trend('reserve_end_ts');
const reservedOk = new Counter('reserved_ok');
const rejected409 = new Counter('rejected_409');
const unexpected = new Counter('unexpected_error');

const json = { headers: { 'Content-Type': 'application/json' } };

export const options = {
  setupTimeout: '10m',
  scenarios: {
    burst: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '2m' },
  },
};

function login(email, password) {
  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password }), json);
  return res.json('accessToken');
}

export function setup() {
  const run = Date.now();
  const adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
  const adminAuth = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminToken}` } };

  const performanceId = http.post(`${BASE_URL}/api/admin/performances`,
    JSON.stringify({ title: `bench-${run}`, venue: 'bench', description: '' }), adminAuth).json();
  const open = new Date(Date.now() - 60000).toISOString().slice(0, 19);
  const start = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 19);
  const scheduleId = http.post(`${BASE_URL}/api/admin/performances/${performanceId}/schedules`,
    JSON.stringify({ startAt: start, ticketOpenAt: open, totalStock: STOCK }), adminAuth).json();

  // 회원 이름을 고정해 두면 반복 실행할 때 두 번째부터는 가입이 409(이미 존재)로 끝나 setup 이 빨라지고,
  // bench 회원이 실행마다 늘어나지 않는다. (정리: DELETE FROM members WHERE email LIKE 'bench%@test.com')
  const emails = Array.from({ length: USERS }, (_, i) => `bench_${i}@test.com`);
  const tokens = [];
  for (let i = 0; i < USERS; i += 50) {
    const chunk = emails.slice(i, i + 50);
    http.batch(chunk.map((email) => ['POST', `${BASE_URL}/api/auth/signup`,
      JSON.stringify({ email, password: 'password123', name: 'bench' }), json]));
    http.batch(chunk.map((email) => ['POST', `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password: 'password123' }), json]))
      .forEach((r) => tokens.push(r.json('accessToken')));
  }
  return { scheduleId, performanceId, tokens, adminToken };
}

export default function (data) {
  const t0 = Date.now();
  const res = http.post(`${BASE_URL}/api/reservations`,
    JSON.stringify({ scheduleId: data.scheduleId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.tokens[__VU - 1]}` } });
  startTs.add(t0);
  endTs.add(Date.now());
  reserveDuration.add(res.timings.duration);

  if (res.status === 200) reservedOk.add(1);
  else if (res.status === 409) rejected409.add(1);
  else {
    unexpected.add(1);
    // 원인 파악용: 상태 코드(0 은 연결 실패/타임아웃)와 응답 일부
    console.warn(`[예상 밖 응답] status=${res.status} error=${res.error || '-'} body=${String(res.body || '').slice(0, 150)}`);
  }
  check(res, { 'ok or sold out': (r) => r.status === 200 || r.status === 409 });
}

export function teardown(data) {
  const auth = { headers: { Authorization: `Bearer ${data.adminToken}` } };
  const stats = http.get(`${BASE_URL}/api/admin/stats`, auth).json();
  let schedule = null;
  for (const p of stats.items) for (const s of p.schedules) if (s.id === data.scheduleId) schedule = s;
  if (schedule) {
    const oversold = Math.max(0, schedule.sold - schedule.totalStock);
    console.log(`[정합성] 재고=${schedule.totalStock}, 확정 예매=${schedule.sold}, 남은 재고=${schedule.remainingStock}, 초과 판매=${oversold}건`);
    // 반복 측정 스크립트(repeat.ps1)가 읽는 기계용 한 줄 (한글 인코딩 문제를 피하려고 ASCII 로 출력)
    console.log(`K6_INTEGRITY ${JSON.stringify({ stock: schedule.totalStock, sold: schedule.sold, remaining: schedule.remainingStock, oversold })}`);
  }
  http.del(`${BASE_URL}/api/admin/performances/${data.performanceId}`, null, auth);
}

export function handleSummary(data) {
  const m = data.metrics;
  const v = (name, key) => (m[name] && m[name].values[key] !== undefined ? m[name].values[key] : 0);
  const windowMs = v('reserve_end_ts', 'max') - v('reserve_start_ts', 'min');
  const total = v('reserved_ok', 'count') + v('rejected_409', 'count') + v('unexpected_error', 'count');
  const tps = windowMs > 0 ? (total / (windowMs / 1000)).toFixed(1) : 'n/a';
  const failRate = total > 0 ? ((v('unexpected_error', 'count') / total) * 100).toFixed(2) : 'n/a';
  const f = (x) => Number(x).toFixed(1);

  const lines = [
    '',
    '========== 예매 부하 테스트 결과 ==========',
    `요청 ${total}건 (성공 ${v('reserved_ok', 'count')}, 재고 소진/충돌 409 ${v('rejected_409', 'count')}, 예상 밖 오류 ${v('unexpected_error', 'count')})`,
    `전체 소요(첫 요청~마지막 응답): ${windowMs}ms  →  TPS ≈ ${tps}`,
    `응답시간(ms): 평균 ${f(v('reserve_duration', 'avg'))} / 중앙값 ${f(v('reserve_duration', 'med'))} / p95 ${f(v('reserve_duration', 'p(95)'))} / 최대 ${f(v('reserve_duration', 'max'))}`,
    `실패율(예상 밖 오류): ${failRate}%`,
    '정합성(초과 판매)은 위쪽의 [정합성] 로그를 확인하세요.',
    '==========================================',
    // 반복 측정 스크립트(repeat.ps1)가 읽는 기계용 한 줄
    `K6_RESULT ${JSON.stringify({
      tps: Number(tps) || 0, avg: Number(f(v('reserve_duration', 'avg'))), med: Number(f(v('reserve_duration', 'med'))),
      p95: Number(f(v('reserve_duration', 'p(95)'))), max: Number(f(v('reserve_duration', 'max'))),
      failPct: Number(failRate) || 0, ok: v('reserved_ok', 'count'), rejected: v('rejected_409', 'count'),
      unexpected: v('unexpected_error', 'count'), windowMs,
    })}`,
    '',
  ];
  return { stdout: lines.join('\n') };
}
