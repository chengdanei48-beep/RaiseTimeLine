-- コメント。削除は論理削除(deleted_at)
CREATE TABLE comments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT       NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       VARCHAR(280) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- 投稿ごとのコメント一覧(古い順)・件数の集計用
CREATE INDEX idx_comments_post ON comments (post_id, created_at, id) WHERE deleted_at IS NULL;
