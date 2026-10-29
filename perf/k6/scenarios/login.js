// ログイン専用。BCrypt(CPU 負荷が高い)の影響を別枠で見る。少数 VU で十分
import { sleep } from 'k6';
import { login, USERS } from '../lib/common.js';

export const options = {
  stages: [
    { duration: '20s', target: 5 },
    { duration: '1m', target: 5 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:login}': ['p(95)<1000'],
  },
};

export default function () {
  login(((__VU + __ITER) % USERS) + 1, 'login');
  sleep(0.5);
}
