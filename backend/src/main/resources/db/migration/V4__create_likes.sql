-- いいね。複合主キーで「1ユーザー1投稿1回」を保証する
CREATE TABLE likes (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id    BIGINT      NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, post_id)
);

-- 投稿ごとのいいね数の集計用
CREATE INDEX idx_likes_post ON likes (post_id);
