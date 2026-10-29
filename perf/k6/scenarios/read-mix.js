// 主力: 閲覧中心の現実的な混合(読み 9 : 書き 1)
import { sleep } from 'k6';
import { get, send, pick, stages, THRESHOLDS } from '../lib/common.js';

export const options = { stages: stages(), thresholds: THRESHOLDS };

const QUERIES = ['perf', 'user_01', '性能', 'perf_user_0', 'test'];

function browseTimeline(scope, name) {
  let cursor = '';
  let first = null;
  for (let page = 0; page < 3; page++) {
    const q = `/api/timeline?scope=${scope}&limit=20${cursor ? '&cursor=' + cursor : ''}`;
    const body = get(q, name).json();
    if (first === null) first = body;
    if (!body.nextCursor) break;
    cursor = body.nextCursor;
    sleep(0.5);
  }
  return first;
}

export default function () {
  const r = Math.random();
  if (r < 0.35) {
    browseTimeline('following', 'timeline_following');
  } else if (r < 0.60) {
    browseTimeline('all', 'timeline_all');
  } else if (r < 0.75) {
    const tl = get('/api/timeline?scope=all&limit=20', 'timeline_all').json();
    const post = pick(tl.items);
    get(`/api/posts/${post.id}`, 'post_detail');
    get(`/api/posts/${post.id}/comments`, 'comments');
  } else if (r < 0.85) {
    const tl = get('/api/timeline?scope=all&limit=20', 'timeline_all').json();
    const author = pick(tl.items).author.id;
    get(`/api/users/${author}`, 'profile');
    get(`/api/users/${author}/posts`, 'user_posts');
  } else if (r < 0.90) {
    get(`/api/users?q=${encodeURIComponent(pick(QUERIES))}`, 'user_search');
  } else {
    // 書き込み(いいねの付け外し。冪等なのでデータは増え続けない)
    const tl = get('/api/timeline?scope=all&limit=20', 'timeline_all').json();
    const post = pick(tl.items);
    send('PUT', `/api/posts/${post.id}/like`, 'like_put');
    send('DELETE', `/api/posts/${post.id}/like`, 'like_delete');
  }
  sleep(1 + Math.random() * 2); // 閲覧の合間(think time)
}
