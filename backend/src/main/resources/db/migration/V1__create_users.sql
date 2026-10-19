CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(15)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(30)  NOT NULL,
    bio           VARCHAR(160),
    avatar_key    VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ユーザーIDは大文字小文字を区別せず重複不可(登録時の表記は保持する)
CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));
