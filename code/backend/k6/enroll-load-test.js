// EnrollGate 2단계 A/B 부하테스트: 정원 카운터 갱신 방식(비관적 락 vs Redis 원자 연산)의
// 신청(enroll) 핫패스 처리량을 동일 조건으로 비교하기 위한 k6 스크립트.
//
// 사용법 (관리자 계정은 미리 시딩되어 있어야 함 — scripts/seed-perf-admin.sh 참고):
//   k6 run -e ADMIN_EMAIL=perf-admin@enrollgate.com -e ADMIN_PASSWORD=perfpass123 \
//          -e CAPACITY=20 -e VUS=100 -e ITERS_PER_VU=3 code/backend/k6/enroll-load-test.js
//
// setup()에서 과목을 새로 만들고 필요한 만큼 학생 계정을 미리 만들어(토큰 캐싱) 실제 측정 구간에서는
// enroll 요청 자체의 처리량만 재도록 한다.
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'perf-admin@enrollgate.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'perfpass123';
const CAPACITY = parseInt(__ENV.CAPACITY || '20', 10);
const VUS = parseInt(__ENV.VUS || '100', 10);
const ITERS_PER_VU = parseInt(__ENV.ITERS_PER_VU || '3', 10);
const RUN_TAG = __ENV.RUN_TAG || 'run';

const enrolledCount = new Counter('enroll_result_enrolled');
const queuedCount = new Counter('enroll_result_queued');
const errorCount = new Counter('enroll_result_error');
const enrollDuration = new Trend('enroll_request_duration', true);

export const options = {
  scenarios: {
    enroll_rush: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERS_PER_VU,
      maxDuration: '2m',
    },
  },
};

// 서버의 LocalDateTime은 타임존이 없는 로컬 벽시계 값이다. toISOString()은 UTC(Z 접미사)를 내보내
// k6와 서버가 같은 머신(같은 로컬 타임존)에서 돌아도 시각이 어긋나므로, 로컬 컴포넌트로 직접 문자열을 만든다.
function toLocalIso(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function jsonHeaders(token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

function login(email, password) {
  const res = http.post(`${BASE}/auth/login`, JSON.stringify({ email, password }), {
    headers: jsonHeaders(),
  });
  if (res.status !== 200) {
    throw new Error(`login failed for ${email}: ${res.status} ${res.body}`);
  }
  return JSON.parse(res.body).accessToken;
}

function signupAndLogin(email, password, name, studentNumber) {
  const signupRes = http.post(
    `${BASE}/auth/signup`,
    JSON.stringify({ email, password, name, studentNumber }),
    { headers: jsonHeaders() }
  );
  if (signupRes.status !== 201 && signupRes.status !== 409) {
    throw new Error(`signup failed for ${email}: ${signupRes.status} ${signupRes.body}`);
  }
  return login(email, password);
}

export function setup() {
  const adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

  const courseRes = http.post(
    `${BASE}/admin/courses`,
    JSON.stringify({
      courseCode: `PERF-${RUN_TAG}`,
      name: '부하테스트용 과목',
      professorName: '측정용',
      department: 'PERF',
      credit: 3,
      capacity: CAPACITY,
      semester: `perf-${RUN_TAG}`,
      enrollmentStartAt: toLocalIso(new Date(Date.now() - 60000)),
      enrollmentEndAt: toLocalIso(new Date(Date.now() + 3600000)),
    }),
    { headers: jsonHeaders(adminToken) }
  );
  if (courseRes.status !== 201) {
    throw new Error(`course creation failed: ${courseRes.status} ${courseRes.body}`);
  }
  const courseId = JSON.parse(courseRes.body).courseId;

  const totalUsers = VUS * ITERS_PER_VU;
  const tokens = [];
  for (let i = 0; i < totalUsers; i++) {
    const email = `perf-${RUN_TAG}-${i}@enrollgate.com`;
    tokens.push(signupAndLogin(email, 'password123', `학생${i}`, `PERF-${RUN_TAG}-${i}`));
  }

  return { courseId, tokens, capacity: CAPACITY };
}

export default function (data) {
  const index = (__VU - 1) * ITERS_PER_VU + (__ITER % ITERS_PER_VU);
  const token = data.tokens[index % data.tokens.length];

  const res = http.post(`${BASE}/courses/${data.courseId}/enroll`, null, {
    headers: jsonHeaders(token),
  });
  enrollDuration.add(res.timings.duration);

  const ok = check(res, {
    'status is 201 or 202': (r) => r.status === 201 || r.status === 202,
  });

  if (!ok) {
    errorCount.add(1);
    return;
  }
  const body = JSON.parse(res.body);
  if (body.status === 'ENROLLED') {
    enrolledCount.add(1);
  } else if (body.status === 'QUEUED') {
    queuedCount.add(1);
  }
}

export function teardown(data) {
  console.log(`[${RUN_TAG}] courseId=${data.courseId} capacity=${data.capacity} users=${data.tokens.length}`);
}
