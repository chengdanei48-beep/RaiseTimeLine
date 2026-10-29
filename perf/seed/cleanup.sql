-- 性能テスト DB の中身を空にする(DB 自体の削除は scripts/perf.ps1 が行う)。*_perf 以外では実行しない
SELECT current_database() ~ '_perf$' AS is_perf \gset
\if :is_perf
\else
\echo 'ERROR: 接続先が *_perf ではありません。中止します'
\quit 1
\endif
TRUNCATE users RESTART IDENTITY CASCADE;
