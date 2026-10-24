-- 投稿の画像(最大4枚)。DB には S3 のオブジェクトキーだけを保存し、URL は保存しない
CREATE TABLE post_images (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT       NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    object_key VARCHAR(255) NOT NULL,
    sort_order SMALLINT     NOT NULL CHECK (sort_order BETWEEN 0 AND 3),
    UNIQUE (post_id, sort_order)
);
