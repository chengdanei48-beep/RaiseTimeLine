-- フォロー。複合主キーで重複を防ぎ、CHECK で自分自身のフォローを禁止する
CREATE TABLE follows (
    follower_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followee_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    CHECK (follower_id <> followee_id)
);

-- フォロワー一覧・フォロワー数の集計用
CREATE INDEX idx_follows_followee ON follows (followee_id, created_at DESC);
