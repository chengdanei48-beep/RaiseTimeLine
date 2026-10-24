-- ユーザー検索(部分一致)用。ILIKE '%キーワード%' をインデックスで高速化する
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_users_username_trgm ON users USING gin (lower(username) gin_trgm_ops);
CREATE INDEX idx_users_display_name_trgm ON users USING gin (lower(display_name) gin_trgm_ops);
