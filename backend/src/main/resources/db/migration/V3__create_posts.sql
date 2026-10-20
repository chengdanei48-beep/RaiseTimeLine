-- 投稿。削除は論理削除(deleted_at)。画像(post_images)は後続で追加する
CREATE TABLE posts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       VARCHAR(280) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- タイムライン用(新しい順・カーソルページング)
CREATE INDEX idx_posts_timeline ON posts (created_at DESC, id DESC) WHERE deleted_at IS NULL;
-- ユーザー別の投稿一覧用
CREATE INDEX idx_posts_user ON posts (user_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
