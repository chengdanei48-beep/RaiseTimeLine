// 書き込み中心: 投稿・コメント・いいね・フォロー。投稿は作成後に削除して件数の増加を抑える
import { sleep } from 'k6';
import { get, send, getSession, pick, stages, THRESHOLDS } from '../lib/common.js';

export const options = { stages: stages(), thresholds: THRESHOLDS };

export default function () {
  const me = getSession();
  const tl = get('/api/timeline?scope=all&limit=20', 'timeline_all').json();
  const post = pick(tl.items);

  const created = send('POST', '/api/posts', 'post_create', {
    body: `負荷テスト投稿 ${__VU}-${__ITER}`,
    imageKeys: [],
  }).json();
  send('POST', `/api/posts/${created.id}/comments`, 'comment_create', { body: '負荷テストコメント' });
  send('PUT', `/api/posts/${post.id}/like`, 'like_put');
  send('DELETE', `/api/posts/${post.id}/like`, 'like_delete');
  if (post.author.id !== me.userId) {
    send('PUT', `/api/users/${post.author.id}/follow`, 'follow_put');
    send('DELETE', `/api/users/${post.author.id}/follow`, 'follow_delete');
  }
  send('DELETE', `/api/posts/${created.id}`, 'post_delete');
  sleep(1 + Math.random() * 2);
}
