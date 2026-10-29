// k6 共通部品。認証は Bearer JWT のみ(CSRF・セッション無し)。トークンは VU ごとに持ち、期限前に取り直す。
import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const PASSWORD = __ENV.PERF_PASSWORD || 'PerfTest#1234';
export const USERS = parseInt(__ENV.PERF_USERS || '50', 10);
const TOKEN_TTL_MS = 12 * 60 * 1000; // アクセストークンは 15 分。余裕を見て取り直す

// VU ごとの状態(k6 は VU ごとにモジュールを別々に読み込む)
let session = null;

export function username(n) {
  return 'perf_user_' + String(n).padStart(3, '0');
}

export function login(n, tag) {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: username(n), password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: tag || 'login' } },
  );
  check(res, { 'login 200': (r) => r.status === 200 });
  if (res.status !== 200) return null;
  const body = res.json();
  return { token: body.accessToken, userId: body.user.id, at: Date.now() };
}

/** この VU の認証済みセッション。VU ごとに固定のユーザーでログインする */
export function getSession() {
  if (session === null || Date.now() - session.at > TOKEN_TTL_MS) {
    session = login(((__VU - 1) % USERS) + 1, 'login_setup');
  }
  return session;
}

export function authParams(name) {
  const s = getSession();
  return {
    headers: { Authorization: `Bearer ${s.token}`, 'Content-Type': 'application/json' },
    tags: { name },
  };
}

export function get(path, name) {
  const res = http.get(`${BASE_URL}${path}`, authParams(name));
  check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return res;
}

export function send(method, path, name, payload) {
  const res = http.request(
    method,
    `${BASE_URL}${path}`,
    payload === undefined ? null : JSON.stringify(payload),
    authParams(name),
  );
  check(res, { [`${name} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return res;
}

export function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

/** 負荷形状。K6_PROFILE=load(既定)|stress|soak */
export function stages() {
  switch (__ENV.K6_PROFILE || 'load') {
    case 'stress':
      return [
        { duration: '1m', target: 20 },
        { duration: '2m', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '1m', target: 0 },
      ];
    case 'soak':
      return [
        { duration: '1m', target: 10 },
        { duration: '28m', target: 10 },
        { duration: '1m', target: 0 },
      ];
    default:
      return [
        { duration: '30s', target: 15 },
        { duration: '4m', target: 15 },
        { duration: '30s', target: 0 },
      ];
  }
}

/** 合否基準。要件「一覧 1 秒以内」・SLO 案「timeline p95 500ms」(docs/07-observability.md 5.2) */
export const THRESHOLDS = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(99)<1000'],
  'http_req_duration{name:timeline_all}': ['p(95)<500'],
  'http_req_duration{name:timeline_following}': ['p(95)<500'],
  checks: ['rate>0.99'],
};
