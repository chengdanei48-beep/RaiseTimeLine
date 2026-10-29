// 動作確認: 1 VU で主要 API を 1 回ずつ。シード投入後に最初に流す
import { check } from 'k6';
import { get, send, getSession, pick } from '../lib/common.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { http_req_failed: ['rate==0'], checks: ['rate==1'] },
};

export default function () {
  const me = getSession();
  const tl = get('/api/timeline?scope=all&limit=20', 'timeline_all').json();
  check(tl, { 'timeline has items': (t) => t.items.length > 0 });
  get('/api/timeline?scope=following&limit=20', 'timeline_following');
  const post = pick(tl.items);
  get(`/api/posts/${post.id}`, 'post_detail');
  get(`/api/posts/${post.id}/comments`, 'comments');
  get(`/api/users/${post.author.id}`, 'profile');
  get(`/api/users/${post.author.id}/posts`, 'user_posts');
  get('/api/users?q=perf_user_00', 'user_search');
  get('/api/auth/me', 'me');
  send('PUT', `/api/posts/${post.id}/like`, 'like_put');
  send('DELETE', `/api/posts/${post.id}/like`, 'like_delete');
  if (post.author.id !== me.userId) {
    send('PUT', `/api/users/${post.author.id}/follow`, 'follow_put');
    send('DELETE', `/api/users/${post.author.id}/follow`, 'follow_delete');
  }
  const created = send('POST', '/api/posts', 'post_create', { body: 'smoke test', imageKeys: [] }).json();
  send('POST', `/api/posts/${created.id}/comments`, 'comment_create', { body: 'smoke comment' });
  send('DELETE', `/api/posts/${created.id}`, 'post_delete');
}
