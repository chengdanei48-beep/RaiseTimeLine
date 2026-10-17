/* RaiseTimeLine 静的プロトタイプ。バックエンド・DBなし。データはメモリ上のみ。 */
(() => {
  'use strict';

  const PAGE = 20;
  const POST_MAX = 280;
  const IMG_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];

  const $header = document.getElementById('header');
  const $main = document.getElementById('main');
  const $toast = document.getElementById('toast');

  /* ---------- ダミーデータ ---------- */
  const MIN = 60000;
  const t0 = Date.now();
  const ago = (m) => t0 - m * MIN;

  const svgImg = (h, label) =>
    'data:image/svg+xml;utf8,' + encodeURIComponent(
      `<svg xmlns='http://www.w3.org/2000/svg' width='640' height='400'><defs>` +
      `<linearGradient id='g' x1='0' y1='0' x2='1' y2='1'><stop offset='0' stop-color='hsl(${h},70%,65%)'/>` +
      `<stop offset='1' stop-color='hsl(${(h + 50) % 360},70%,42%)'/></linearGradient></defs>` +
      `<rect width='640' height='400' fill='url(#g)'/>` +
      `<text x='320' y='215' font-size='42' text-anchor='middle' fill='white' font-family='sans-serif'>${label}</text></svg>`);

  const state = {
    me: null,
    users: [
      { id: 1, userId: 'taro', displayName: '山田太郎', password: 'password123', bio: 'Java と React を勉強中です。よろしくお願いします。', avatar: null },
      { id: 2, userId: 'hanako', displayName: '佐藤花子', password: 'password123', bio: 'カフェ巡りと写真が好きです☕', avatar: null },
      { id: 3, userId: 'jiro', displayName: '田中次郎', password: 'password123', bio: 'ランニング・読書・猫。', avatar: null },
      { id: 4, userId: 'hanayama', displayName: '花山太郎', password: 'password123', bio: '', avatar: null },
      { id: 5, userId: 'ken', displayName: '鈴木健', password: 'password123', bio: 'バックエンドエンジニア。PostgreSQL が好き。', avatar: null },
      { id: 6, userId: 'mika', displayName: '高橋美香', password: 'password123', bio: '毎日ごはんを作っています🍳', avatar: null },
      { id: 7, userId: 'Sora_dev', displayName: '小林空', password: 'password123', bio: 'フロントエンド修行中。', avatar: null },
      { id: 8, userId: 'yuki', displayName: '伊藤雪', password: 'password123', bio: '映画と音楽。', avatar: null },
    ],
    posts: [],
    comments: [],
    follows: [],
    tab: 'following',
  };
  let seq = { user: 9, post: 1, comment: 1 };

  (function seed() {
    const texts = [
      '今日はいい天気ですね。散歩に出かけます。',
      '朝からカレーを作りました🍛 スパイスが決め手です。',
      'Spring Boot の勉強中。DI の仕組みが少しずつ分かってきた!',
      '週末は友達と映画を観に行く予定です。おすすめがあれば教えてください。',
      '新しいキーボードが届いた。打鍵感が最高。',
      '夜の街を撮ってみました。',
      '読書メモ:「小さな習慣」を読み終えた。毎日5分から始めるのが大事らしい。',
      'React の useState でハマった…再レンダリングは奥が深い。',
      'お昼は近所のラーメン屋へ。行列でも並ぶ価値あり。',
      '今日のコーヒーは浅煎りのエチオピア。フルーティーで好き。',
      '雨の日は家で作業がはかどる。',
      'ランニング5km完了!少しずつペースが上がってきた。',
      '猫がキーボードの上で寝てしまって仕事にならない🐈',
      'PostgreSQL のインデックス設計、実行計画を見ると面白い。',
    ];
    const cmts = ['いいですね!', '分かります〜', 'ぜひ詳しく聞きたいです。', '素敵な一日になりますように。', '私も同じです😊', 'ありがとうございます、参考になります。'];
    for (let i = 0; i < 38; i++) {
      const author = state.users[(i * 3 + 1) % 8];
      const post = {
        id: seq.post++,
        userId: author.id,
        body: texts[i % texts.length],
        images: [],
        createdAt: ago(5 + i * 37 + (i % 4) * 5),
        likes: [],
      };
      if (i % 5 === 0) {
        const n = 1 + ((i / 5) % 4);
        for (let k = 0; k < n; k++) post.images.push(svgImg((i * 47 + k * 70) % 360, `Photo ${k + 1}`));
      }
      const likeCount = (i * 5) % 8;
      for (let k = 0; k < likeCount; k++) post.likes.push(state.users[(i + k + 2) % 8].id);
      post.likes = [...new Set(post.likes)];
      state.posts.push(post);
      if (i % 3 === 0) {
        const n = 1 + (i % 3) + (i % 2);
        for (let k = 0; k < n; k++) {
          state.comments.push({
            id: seq.comment++, postId: post.id,
            userId: state.users[(i + k + 4) % 8].id,
            body: cmts[(i + k) % cmts.length],
            createdAt: post.createdAt + (k + 1) * 3 * MIN,
          });
        }
      }
    }
    [[1, 2], [1, 3], [2, 1], [2, 3], [2, 6], [3, 1], [4, 2], [5, 1], [5, 2], [6, 1], [7, 1], [7, 3], [8, 2]]
      .forEach(([from, to], i) => state.follows.push({ from, to, t: i }));
  })();

  /* ---------- ユーティリティ ---------- */
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const len = (s) => [...s.trim()].length;
  const byId = (id) => state.users.find((u) => u.id === Number(id));
  const postById = (id) => state.posts.find((p) => p.id === Number(id));
  const isFollowing = (from, to) => state.follows.some((f) => f.from === from && f.to === to);
  const followingCount = (id) => state.follows.filter((f) => f.from === id).length;
  const followerCount = (id) => state.follows.filter((f) => f.to === id).length;
  const commentCount = (pid) => state.comments.filter((c) => c.postId === pid).length;

  function relTime(ts) {
    const m = Math.floor((Date.now() - ts) / MIN);
    if (m < 1) return 'たった今';
    if (m < 60) return `${m}分前`;
    if (m < 60 * 24) return `${Math.floor(m / 60)}時間前`;
    return `${Math.floor(m / 60 / 24)}日前`;
  }

  let toastTimer;
  function toast(msg, isError) {
    $toast.textContent = msg;
    $toast.className = 'toast show' + (isError ? ' error' : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { $toast.className = 'toast'; }, 2600);
  }

  const go = (hash) => { location.hash = hash; };

  function avatar(u, cls = '') {
    if (u.avatar) return `<img class="avatar ${cls}" src="${u.avatar}" alt="">`;
    const hue = (u.id * 67) % 360;
    return `<span class="avatar ${cls}" style="background:hsl(${hue},55%,50%)">${esc([...u.displayName][0] || '?')}</span>`;
  }

  function imagesHtml(images) {
    if (!images.length) return '';
    return `<div class="images n${images.length}">${images.map((src) => `<img src="${src}" alt="添付画像">`).join('')}</div>`;
  }

  function followBtn(uid) {
    if (!state.me || uid === state.me.id) return '';
    return isFollowing(state.me.id, uid)
      ? `<button class="btn btn-following" data-act="follow" data-uid="${uid}"><span class="off-hover">フォロー中 ✓</span><span class="on-hover">解除する</span></button>`
      : `<button class="btn" data-act="follow" data-uid="${uid}">フォローする</button>`;
  }

  function postCard(p, detail) {
    const u = byId(p.userId);
    const liked = p.likes.includes(state.me.id);
    // 本仕様どおり、インプレッション数・リポスト/引用のボタンは表示しない
    return `<article class="post${detail ? ' detail' : ''}" data-id="${p.id}">
      <a href="#/users/${u.id}">${avatar(u)}</a>
      <div class="post-main">
        <div class="post-head">
          <a class="name" href="#/users/${u.id}">${esc(u.displayName)}</a>
          <a class="handle" href="#/users/${u.id}">@${esc(u.userId)}</a>
          <span class="dot">·</span>
          <a class="time" href="#/posts/${p.id}">${relTime(p.createdAt)}</a>
          <span class="grow"></span>
          ${u.id === state.me.id ? `<button class="btn-danger-text" data-act="del-post" data-id="${p.id}">削除</button>` : ''}
        </div>
        <div class="post-body">${esc(p.body)}</div>
        ${imagesHtml(p.images)}
        <div class="post-actions">
          <a class="act" href="#/posts/${p.id}" title="コメント">💬 ${commentCount(p.id)}</a>
          <button class="act like${liked ? ' liked' : ''}" data-act="like" data-id="${p.id}" title="いいね">${liked ? '♥' : '♡'} ${p.likes.length}</button>
        </div>
      </div>
    </article>`;
  }

  function userRow(u, withBio) {
    return `<div class="user-row" data-href="#/users/${u.id}">
      ${avatar(u)}
      <div class="info">
        <div class="name">${esc(u.displayName)} <span class="muted" style="font-weight:400">@${esc(u.userId)}</span></div>
        ${withBio && u.bio ? `<div class="bio">${esc(u.bio)}</div>` : ''}
      </div>
      ${followBtn(u.id)}
    </div>`;
  }

  function refreshPost(id) {
    const p = postById(id);
    document.querySelectorAll(`.post[data-id="${id}"]`).forEach((el) => {
      if (p) el.outerHTML = postCard(p, el.classList.contains('detail'));
    });
  }

  /* ---------- 「もっと見る」付きリスト ---------- */
  let lists = {};
  function setupList(key, cfg) {
    lists[key] = Object.assign({ shown: lists[key] ? lists[key].shown : PAGE }, cfg);
  }
  function renderList(key) {
    const L = lists[key];
    const host = document.querySelector(`[data-list="${key}"]`);
    if (!L || !host) return;
    const items = L.get();
    if (!items.length) { host.innerHTML = typeof L.empty === 'function' ? L.empty() : L.empty; return; }
    host.innerHTML = items.slice(0, L.shown).map(L.row).join('') +
      (items.length > L.shown ? `<div class="more"><button class="btn btn-outline" data-act="more" data-key="${key}">もっと見る</button></div>` : '');
  }
  const renderAllLists = () => Object.keys(lists).forEach(renderList);

  /* ---------- 画面 ---------- */
  const pages = {};
  let current = { name: 'timeline' };
  let compose = { images: [] };
  let editAvatar; // undefined=変更なし / null=削除 / 文字列=新しい画像

  function headerHtml() {
    const me = state.me;
    return `<header class="app-header"><div class="app-header-inner">
      <a class="logo" href="#/">RaiseTimeLine</a>
      <a class="header-search" href="#/search">🔍 ユーザーを検索</a>
      <a class="header-me" href="#/users/${me.id}">${avatar(me, 'sm')}<span class="name">${esc(me.displayName)}</span></a>
      <button class="btn btn-ghost" data-act="logout" style="padding:5px 12px">ログアウト</button>
    </div></header>`;
  }

  const backBar = (title) => `<div class="page-head"><button class="back" data-act="back">← 戻る</button>${title ? `<h1 class="page-title">${esc(title)}</h1>` : ''}</div>`;

  /* S-01 新規登録 */
  pages.signup = () => `<div class="auth-wrap card card-pad">
    <h1>RaiseTimeLine</h1><p class="lead">アカウントを作成</p>
    <form data-form="signup" novalidate>
      <div class="field"><label for="su-id">ユーザーID</label>
        <input id="su-id" type="text" name="userId" autocomplete="off">
        <div class="hint">3〜15文字の半角英数字と _ (大文字小文字は区別されません)</div><div class="error" data-err="userId"></div></div>
      <div class="field"><label for="su-name">表示名</label>
        <input id="su-name" type="text" name="displayName" autocomplete="off">
        <div class="hint">1〜30文字</div><div class="error" data-err="displayName"></div></div>
      <div class="field"><label for="su-pw">パスワード</label>
        <input id="su-pw" type="password" name="password" autocomplete="new-password">
        <div class="hint">8〜72文字</div><div class="error" data-err="password"></div></div>
      <div class="field"><label for="su-pw2">パスワード(確認)</label>
        <input id="su-pw2" type="password" name="password2" autocomplete="new-password">
        <div class="error" data-err="password2"></div></div>
      <button class="btn full" type="submit">登録する</button>
    </form>
    <div class="auth-links">アカウントをお持ちの方は <a href="#/login">ログイン</a></div>
  </div>`;

  /* S-02 ログイン */
  pages.login = () => `<div class="auth-wrap card card-pad">
    <h1>RaiseTimeLine</h1><p class="lead">ログイン</p>
    <form data-form="login" novalidate>
      <div class="form-error" data-err="form"></div>
      <div class="field"><label for="li-id">ユーザーID</label><input id="li-id" type="text" name="userId" autocomplete="username"></div>
      <div class="field"><label for="li-pw">パスワード</label><input id="li-pw" type="password" name="password" autocomplete="current-password"></div>
      <button class="btn full" type="submit">ログイン</button>
    </form>
    <div class="auth-links">はじめての方は <a href="#/signup">新規登録</a></div>
    <div class="demo-hint">デモ用アカウント: <b>taro</b> / <b>password123</b><br>(hanako, jiro, ken, mika なども同じパスワード)</div>
  </div>`;

  /* S-03 タイムライン */
  pages.timeline = () => {
    setupList('tl', {
      get: () => state.posts
        .filter((p) => state.tab === 'all' || p.userId === state.me.id || isFollowing(state.me.id, p.userId))
        .sort((a, b) => b.createdAt - a.createdAt),
      row: (p) => postCard(p),
      empty: () => (state.tab === 'following'
        ? '<div class="empty">まだ投稿がありません。ユーザーを検索してフォローしてみましょう<br><a href="#/search">ユーザーを検索する</a></div>'
        : '<div class="empty">まだ投稿がありません</div>'),
    });
    return `<form class="card compose" data-form="compose" novalidate>
      <div class="compose-row">${avatar(state.me)}
        <textarea name="body" data-limit="${POST_MAX}" placeholder="いまどうしてる?(280字)" aria-label="投稿本文"></textarea></div>
      <div class="previews" id="previews"></div>
      <div class="form-error" id="compose-err" style="margin:6px 0 0 56px"></div>
      <div class="compose-foot">
        <label class="file-btn" id="file-btn">🖼 画像を追加 (<span id="img-count">0</span>/4)
          <input type="file" id="file-input" accept="image/jpeg,image/png,image/gif,image/webp" multiple></label>
        <span class="spacer"></span>
        <span class="counter" id="compose-counter">残り ${POST_MAX}</span>
        <button class="btn" type="submit" id="compose-submit" disabled>投稿する</button>
      </div>
    </form>
    <div class="tabs">
      <button class="tab${state.tab === 'following' ? ' active' : ''}" data-act="tab" data-tab="following">フォロー中</button>
      <button class="tab${state.tab === 'all' ? ' active' : ''}" data-act="tab" data-tab="all">全体</button>
    </div>
    <div class="card" data-list="tl"></div>`;
  };

  /* S-04 投稿詳細 */
  pages.post = (r) => {
    const p = postById(r.id);
    if (!p) return `${backBar()}<div class="card empty">投稿が見つかりません。<br><a href="#/">タイムラインへ戻る</a></div>`;
    return `${backBar()}
      <div class="card" id="detail">${postCard(p, true)}</div>
      <form class="card comment-form" data-form="comment" novalidate>
        <textarea name="body" data-limit="${POST_MAX}" placeholder="コメントを入力(280字)" aria-label="コメント"></textarea>
        <div class="comment-foot"><span class="counter" id="comment-counter">残り ${POST_MAX}</span>
          <button class="btn" type="submit" id="comment-submit" disabled>返信する</button></div>
      </form>
      <div class="card" id="comments"></div>`;
  };

  function renderComments() {
    const host = document.getElementById('comments');
    if (!host) return;
    const list = state.comments.filter((c) => c.postId === current.id).sort((a, b) => a.createdAt - b.createdAt);
    host.innerHTML = list.length ? list.map((c) => {
      const u = byId(c.userId);
      return `<article class="post" data-comment="${c.id}">
        <a href="#/users/${u.id}">${avatar(u)}</a>
        <div class="post-main">
          <div class="post-head">
            <a class="name" href="#/users/${u.id}">${esc(u.displayName)}</a>
            <a class="handle" href="#/users/${u.id}">@${esc(u.userId)}</a><span class="dot">·</span>
            <span class="time">${relTime(c.createdAt)}</span><span class="grow"></span>
            ${u.id === state.me.id ? `<button class="btn-danger-text" data-act="del-comment" data-id="${c.id}">削除</button>` : ''}
          </div>
          <div class="post-body" style="margin-bottom:0">${esc(c.body)}</div>
        </div></article>`;
    }).join('') : '<div class="empty">まだコメントがありません</div>';
  }

  /* S-05 プロフィール */
  pages.profile = (r) => {
    const u = byId(r.id);
    if (!u) return `${backBar()}<div class="card empty">ユーザーが見つかりません。<br><a href="#/">タイムラインへ戻る</a></div>`;
    const mine = u.id === state.me.id;
    setupList('profile', {
      get: () => state.posts.filter((p) => p.userId === u.id).sort((a, b) => b.createdAt - a.createdAt),
      row: (p) => postCard(p),
      empty: '<div class="empty">まだ投稿がありません</div>',
    });
    const postN = state.posts.filter((p) => p.userId === u.id).length;
    return `${backBar()}
      <div class="card profile-top">
        <div class="profile-row">${avatar(u, 'lg')}
          <div class="profile-info"><h2>${esc(u.displayName)}</h2><div class="muted">@${esc(u.userId)}</div></div>
          ${mine ? '<a class="btn btn-outline" href="#/settings/profile">編集</a>' : followBtn(u.id)}
        </div>
        ${u.bio ? `<div class="profile-bio">${esc(u.bio)}</div>` : '<div class="profile-bio muted">自己紹介はまだありません</div>'}
        <div class="counts">
          <span><b>${postN}</b> 投稿</span>
          <a href="#/users/${u.id}/following"><b>${followingCount(u.id)}</b> フォロー中</a>
          <a href="#/users/${u.id}/followers"><b>${followerCount(u.id)}</b> フォロワー</a>
        </div>
      </div>
      <div class="card" data-list="profile"></div>`;
  };

  /* S-06 プロフィール編集 */
  pages.edit = () => {
    const me = state.me;
    editAvatar = undefined;
    return `${backBar('プロフィール編集')}
      <form class="card card-pad" data-form="edit" novalidate>
        <div class="field"><label>アイコン画像</label>
          <div class="avatar-edit"><span id="edit-avatar">${avatar(me, 'lg')}</span>
            <div><label class="file-btn" style="display:inline-block">画像を選択<input type="file" id="avatar-input" accept="image/jpeg,image/png,image/gif,image/webp"></label>
              <button type="button" class="btn-danger-text" data-act="avatar-del">削除</button>
              <div class="hint">JPEG / PNG / GIF / WebP、2MB以下</div></div></div>
          <div class="error" data-err="avatar"></div></div>
        <div class="field"><label for="ed-name">表示名</label>
          <input id="ed-name" type="text" name="displayName" value="${esc(me.displayName)}"><div class="error" data-err="displayName"></div></div>
        <div class="field"><label for="ed-bio">自己紹介</label>
          <textarea id="ed-bio" name="bio" rows="4" data-limit="160">${esc(me.bio)}</textarea>
          <div class="hint"><span class="counter" id="bio-counter">${160 - len(me.bio)}</span> 文字まで(160文字以内)</div><div class="error" data-err="bio"></div></div>
        <div class="field"><label>ユーザーID</label><div>@${esc(me.userId)} <span class="muted">(変更できません)</span></div></div>
        <div style="display:flex;gap:10px"><button class="btn" type="submit">保存</button>
          <a class="btn btn-ghost" href="#/users/${me.id}">キャンセル</a></div>
      </form>`;
  };

  /* S-07 ユーザー検索 */
  pages.search = (r) => {
    const raw = r.q.trim().replace(/^@+/, '').trim();
    let msg = '';
    let q = '';
    if (!r.q.trim()) msg = '<div class="empty">ユーザーIDまたは表示名を入力してください</div>';
    else if (!raw || [...raw].length > 50) msg = '<div class="empty">1〜50文字で入力してください</div>';
    else q = raw.toLowerCase();
    if (q) {
      setupList('search', {
        get: () => state.users
          .filter((u) => u.id !== state.me.id && (u.userId.toLowerCase().includes(q) || u.displayName.toLowerCase().includes(q)))
          .sort((a, b) => a.userId.toLowerCase().localeCompare(b.userId.toLowerCase())),
        row: (u) => userRow(u, true),
        empty: '<div class="empty">該当するユーザーが見つかりません</div>',
      });
    }
    return `${backBar()}
      <form class="card search-box" data-form="search" novalidate>
        <input type="text" name="q" value="${esc(r.q)}" placeholder="ユーザーIDまたは表示名で検索" aria-label="検索" autofocus>
        <button class="btn" type="submit">検索</button>
      </form>
      ${q ? '<div class="card" data-list="search"></div>' : `<div class="card">${msg}</div>`}`;
  };

  /* S-08 フォロー中・フォロワー一覧 */
  pages.follows = (r) => {
    const u = byId(r.id);
    if (!u) return `${backBar()}<div class="card empty">ユーザーが見つかりません。<br><a href="#/">タイムラインへ戻る</a></div>`;
    const following = r.kind === 'following';
    setupList('follows', {
      get: () => state.follows
        .filter((f) => (following ? f.from : f.to) === u.id)
        .sort((a, b) => b.t - a.t)
        .map((f) => byId(following ? f.to : f.from)),
      row: (x) => userRow(x, false),
      empty: '<div class="empty">まだいません</div>',
    });
    return `${backBar(u.displayName)}
      <div class="tabs">
        <a class="tab${following ? ' active' : ''}" href="#/users/${u.id}/following">フォロー中</a>
        <a class="tab${following ? '' : ' active'}" href="#/users/${u.id}/followers">フォロワー</a>
      </div>
      <div class="card" data-list="follows"></div>`;
  };

  /* ---------- ルーティング ---------- */
  function parse() {
    const [path, qs] = (location.hash.slice(1) || '/').split('?');
    const q = new URLSearchParams(qs || '');
    let m;
    if (path === '/login') return { name: 'login' };
    if (path === '/signup') return { name: 'signup' };
    if (path === '/') return { name: 'timeline' };
    if ((m = path.match(/^\/posts\/(\d+)$/))) return { name: 'post', id: Number(m[1]) };
    if ((m = path.match(/^\/users\/(\d+)$/))) return { name: 'profile', id: Number(m[1]) };
    if ((m = path.match(/^\/users\/(\d+)\/(following|followers)$/))) return { name: 'follows', id: Number(m[1]), kind: m[2] };
    if (path === '/settings/profile') return { name: 'edit' };
    if (path === '/search') return { name: 'search', q: q.get('q') || '' };
    return null;
  }

  function render(keepScroll) {
    const y = window.scrollY;
    const auth = current.name === 'login' || current.name === 'signup';
    $header.innerHTML = state.me && !auth ? headerHtml() : '';
    $main.innerHTML = pages[current.name](current);
    renderAllLists();
    if (current.name === 'post') renderComments();
    if (current.name === 'timeline') renderCompose();
    window.scrollTo(0, keepScroll ? y : 0);
  }

  function route() {
    const r = parse();
    if (!r) { go('#/'); return; }
    const auth = r.name === 'login' || r.name === 'signup';
    if (!state.me && !auth) { go('#/login'); return; } // 未ログインは S-02 へ
    if (state.me && auth) { go('#/'); return; }
    if (r.name === 'edit' && !state.me) return;
    current = r;
    lists = {};
    if (r.name === 'timeline') compose = { images: [] };
    render(false);
  }

  /* ---------- 投稿フォーム ---------- */
  function renderCompose() {
    const f = document.querySelector('[data-form="compose"]');
    if (!f) return;
    document.getElementById('previews').innerHTML = compose.images.map((src, i) =>
      `<div class="preview"><img src="${src}" alt="プレビュー"><button type="button" data-act="rm-img" data-i="${i}" aria-label="画像を取り消す">✕</button></div>`).join('');
    document.getElementById('previews').style.display = compose.images.length ? '' : 'none';
    document.getElementById('img-count').textContent = compose.images.length;
    document.getElementById('file-btn').classList.toggle('disabled', compose.images.length >= 4);
    updateCounter(f.elements.body, 'compose-counter', 'compose-submit');
  }

  function updateCounter(ta, counterId, btnId) {
    const n = len(ta.value);
    const c = document.getElementById(counterId);
    c.textContent = `残り ${POST_MAX - n}`;
    c.classList.toggle('over', n > POST_MAX);
    document.getElementById(btnId).disabled = n < 1 || n > POST_MAX;
  }

  const readFile = (file) => URL.createObjectURL(file);
  function validateImage(file, maxMB) {
    if (!IMG_TYPES.includes(file.type)) return `${file.name}: JPEG/PNG/GIF/WebP の画像を選んでください`;
    if (file.size > maxMB * 1024 * 1024) return `${file.name}: ${maxMB}MB以下の画像を選んでください`;
    return '';
  }

  /* ---------- イベント(委任) ---------- */
  const setErr = (form, name, msg) => {
    const el = form.querySelector(`[data-err="${name}"]`);
    if (el) el.textContent = msg || '';
    return !!msg;
  };

  const acts = {
    back: () => (history.length > 1 ? history.back() : go('#/')),
    logout: () => { state.me = null; go('#/login'); },
    tab: (el) => {
      state.tab = el.dataset.tab;
      try { sessionStorage.setItem('rtl-tab', state.tab); } catch (e) { /* 保持できなくても動作に支障なし */ }
      lists.tl.shown = PAGE;
      document.querySelectorAll('.tab[data-tab]').forEach((b) => b.classList.toggle('active', b.dataset.tab === state.tab));
      renderList('tl'); // 投稿フォームの入力内容は保持する
    },
    more: (el) => { lists[el.dataset.key].shown += PAGE; renderList(el.dataset.key); },
    like: (el) => {
      const p = postById(el.dataset.id);
      const i = p.likes.indexOf(state.me.id);
      if (i >= 0) p.likes.splice(i, 1); else p.likes.push(state.me.id); // 1ユーザー1回。再押下で取り消し
      refreshPost(p.id);
    },
    'del-post': (el) => {
      if (!confirm('この投稿を削除しますか?(コメント・いいね・画像も見られなくなります)')) return;
      const id = Number(el.dataset.id);
      state.posts = state.posts.filter((p) => p.id !== id);
      state.comments = state.comments.filter((c) => c.postId !== id);
      toast('投稿を削除しました');
      if (current.name === 'post') { go('#/'); return; }
      if (current.name === 'profile') render(true); else renderAllLists();
    },
    'del-comment': (el) => {
      if (!confirm('このコメントを削除しますか?')) return;
      state.comments = state.comments.filter((c) => c.id !== Number(el.dataset.id));
      renderComments();
      refreshPost(current.id);
      toast('コメントを削除しました');
    },
    follow: (el) => {
      const uid = Number(el.dataset.uid);
      if (uid === state.me.id) { toast('自分自身はフォローできません', true); return; }
      const i = state.follows.findIndex((f) => f.from === state.me.id && f.to === uid);
      if (i >= 0) state.follows.splice(i, 1);
      else state.follows.push({ from: state.me.id, to: uid, t: Date.now() });
      document.querySelectorAll(`[data-act="follow"][data-uid="${uid}"]`).forEach((b) => { b.outerHTML = followBtn(uid); });
      if (current.name === 'profile') render(true); // フォロワー数を更新
    },
    'rm-img': (el) => { compose.images.splice(Number(el.dataset.i), 1); renderCompose(); },
    'avatar-del': () => { editAvatar = null; document.getElementById('edit-avatar').innerHTML = avatar({ ...state.me, avatar: null }, 'lg'); },
  };

  document.addEventListener('click', (e) => {
    const a = e.target.closest('[data-act]');
    if (a && acts[a.dataset.act]) { e.preventDefault(); acts[a.dataset.act](a); return; }
    const row = e.target.closest('[data-href]');
    if (row) go(row.dataset.href);
  });

  document.addEventListener('input', (e) => {
    const t = e.target;
    if (t.name === 'body' && t.closest('[data-form="compose"]')) updateCounter(t, 'compose-counter', 'compose-submit');
    if (t.name === 'body' && t.closest('[data-form="comment"]')) updateCounter(t, 'comment-counter', 'comment-submit');
    if (t.id === 'ed-bio') document.getElementById('bio-counter').textContent = 160 - len(t.value);
  });

  document.addEventListener('change', (e) => {
    const t = e.target;
    if (t.id === 'file-input') {
      const errEl = document.getElementById('compose-err');
      errEl.textContent = '';
      for (const file of t.files) {
        if (compose.images.length >= 4) { errEl.textContent = '画像は最大4枚までです'; break; }
        const err = validateImage(file, 5);
        if (err) { errEl.textContent = err; continue; }
        compose.images.push(readFile(file));
      }
      t.value = '';
      renderCompose();
    }
    if (t.id === 'avatar-input') {
      const file = t.files[0];
      const form = t.closest('form');
      if (!file) return;
      const err = validateImage(file, 2);
      if (setErr(form, 'avatar', err)) { t.value = ''; return; }
      editAvatar = readFile(file);
      document.getElementById('edit-avatar').innerHTML = avatar({ ...state.me, avatar: editAvatar }, 'lg');
    }
  });

  const submits = {
    login: (f) => {
      const id = f.elements.userId.value.trim();
      const u = state.users.find((x) => x.userId.toLowerCase() === id.toLowerCase());
      if (!id || !f.elements.password.value || !u || u.password !== f.elements.password.value) {
        setErr(f, 'form', 'ユーザーIDまたはパスワードが正しくありません'); return;
      }
      state.me = u;
      go('#/');
    },
    signup: (f) => {
      const id = f.elements.userId.value.trim();
      const name = f.elements.displayName.value;
      const pw = f.elements.password.value;
      let bad = false;
      bad = setErr(f, 'userId',
        !/^[A-Za-z0-9_]{3,15}$/.test(id) ? 'ユーザーIDは3〜15文字の半角英数字と _ で入力してください'
          : state.users.some((u) => u.userId.toLowerCase() === id.toLowerCase()) ? 'このユーザーIDは既に使われています' : '') || bad;
      bad = setErr(f, 'displayName', len(name) < 1 || len(name) > 30 ? '表示名は1〜30文字で入力してください' : '') || bad;
      bad = setErr(f, 'password', pw.length < 8 || pw.length > 72 ? 'パスワードは8〜72文字で入力してください' : '') || bad;
      bad = setErr(f, 'password2', pw !== f.elements.password2.value ? 'パスワードが一致しません' : '') || bad;
      if (bad) return;
      const u = { id: seq.user++, userId: id, displayName: name.trim(), password: pw, bio: '', avatar: null };
      state.users.push(u);
      state.me = u; // 登録後はログイン状態
      go('#/');
    },
    compose: (f) => {
      const body = f.elements.body.value.trim();
      if (len(body) < 1) { document.getElementById('compose-err').textContent = '本文を入力してください(画像のみの投稿はできません)'; return; }
      if (len(body) > POST_MAX) return;
      state.posts.push({ id: seq.post++, userId: state.me.id, body, images: compose.images.slice(), createdAt: Date.now(), likes: [] });
      compose = { images: [] };
      f.reset();
      document.getElementById('compose-err').textContent = '';
      renderCompose();
      renderList('tl');
      toast('投稿しました');
    },
    comment: (f) => {
      const body = f.elements.body.value.trim();
      if (len(body) < 1 || len(body) > POST_MAX) return;
      state.comments.push({ id: seq.comment++, postId: current.id, userId: state.me.id, body, createdAt: Date.now() });
      f.reset();
      updateCounter(f.elements.body, 'comment-counter', 'comment-submit');
      renderComments();
      refreshPost(current.id);
    },
    search: (f) => go('#/search?q=' + encodeURIComponent(f.elements.q.value)),
    edit: (f) => {
      const name = f.elements.displayName.value;
      const bio = f.elements.bio.value;
      let bad = setErr(f, 'displayName', len(name) < 1 || len(name) > 30 ? '表示名は1〜30文字で入力してください' : '');
      bad = setErr(f, 'bio', len(bio) > 160 ? '自己紹介は160文字以内で入力してください' : '') || bad;
      if (bad) return;
      state.me.displayName = name.trim();
      state.me.bio = bio.trim();
      if (editAvatar !== undefined) state.me.avatar = editAvatar;
      toast('プロフィールを保存しました');
      go('#/users/' + state.me.id);
    },
  };

  document.addEventListener('submit', (e) => {
    const f = e.target.closest('[data-form]');
    if (!f) return;
    e.preventDefault();
    submits[f.dataset.form](f);
  });

  try { state.tab = sessionStorage.getItem('rtl-tab') === 'all' ? 'all' : 'following'; } catch (e) { /* 既定のまま */ }
  window.addEventListener('hashchange', route);
  route();
})();
