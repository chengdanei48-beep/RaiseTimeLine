-- 性能テスト用のシード。専用 DB(*_perf)にだけ投入する。psql -v users=50 -v posts=5000 -f seed.sql
\if :{?users}
\else
\set users 50
\endif
\if :{?posts}
\else
\set posts 5000
\endif

-- 安全装置: 開発 DB には投入しない
SELECT current_database() ~ '_perf$' AS is_perf \gset
\if :is_perf
\else
\echo 'ERROR: 接続先が *_perf ではありません。中止します'
\quit 1
\endif

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 全ユーザー共通のパスワード 'PerfTest#1234'(BCrypt 強度 10。アプリの BCryptPasswordEncoder と互換)
INSERT INTO users (username, password_hash, display_name, bio)
SELECT 'perf_user_' || lpad(g::text, 3, '0'),
       (SELECT crypt('PerfTest#1234', gen_salt('bf', 10))),
       '性能テスト' || g,
       '性能テスト用ユーザー'
FROM generate_series(1, :users) g;

-- 投稿: 過去 90 日に分散。ユーザーはほぼ均等
INSERT INTO posts (user_id, body, created_at, updated_at)
SELECT u.id,
       '性能テスト投稿 ' || g || ' ' || repeat('あ', 20 + (g % 100)),
       now() - (g * interval '1 minute') * (90 * 24 * 60 / :posts::float8),
       now() - (g * interval '1 minute') * (90 * 24 * 60 / :posts::float8)
FROM generate_series(1, :posts) g
JOIN LATERAL (SELECT id FROM users WHERE username LIKE 'perf\_user\_%'
              ORDER BY id OFFSET (g % :users) LIMIT 1) u ON true;

-- フォロー: 各ユーザーが次の 10 人をフォロー
INSERT INTO follows (follower_id, followee_id)
SELECT a.id, b.id
FROM (SELECT id, row_number() OVER (ORDER BY id) - 1 AS n FROM users WHERE username LIKE 'perf\_user\_%') a
JOIN (SELECT id, row_number() OVER (ORDER BY id) - 1 AS n FROM users WHERE username LIKE 'perf\_user\_%') b
  ON b.n <> a.n AND ((b.n - a.n + :users) % :users) BETWEEN 1 AND 10
ON CONFLICT DO NOTHING;

-- いいね: 投稿ごとに 0〜5 件
INSERT INTO likes (user_id, post_id)
SELECT u.id, p.id
FROM posts p
CROSS JOIN generate_series(1, 5) k
JOIN LATERAL (SELECT id FROM users WHERE username LIKE 'perf\_user\_%'
              ORDER BY id OFFSET ((p.id + k) % :users) LIMIT 1) u ON true
WHERE k <= (p.id % 6)
ON CONFLICT DO NOTHING;

-- コメント: 投稿ごとに 0〜3 件
INSERT INTO comments (post_id, user_id, body, created_at)
SELECT p.id, u.id, '性能テストコメント ' || k, p.created_at + (k * interval '1 minute')
FROM posts p
CROSS JOIN generate_series(1, 3) k
JOIN LATERAL (SELECT id FROM users WHERE username LIKE 'perf\_user\_%'
              ORDER BY id OFFSET ((p.id * 7 + k) % :users) LIMIT 1) u ON true
WHERE k <= (p.id % 4);

ANALYZE;

SELECT (SELECT count(*) FROM users)    AS users,
       (SELECT count(*) FROM posts)    AS posts,
       (SELECT count(*) FROM follows)  AS follows,
       (SELECT count(*) FROM likes)    AS likes,
       (SELECT count(*) FROM comments) AS comments;
