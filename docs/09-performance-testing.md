# パフォーマンステスト

任意のタイミングで**手動実行**する性能テストの設計と手順。CI(`.github/workflows/ci.yml`)や `./mvnw verify` には含めない。

## 1. 目的と合格基準

| 観点 | 基準 | 出典 |
|---|---|---|
| 一覧の表示 | 学習規模で 1 秒以内 | 要件定義書 6.1 |
| `/api/timeline` | p95 ≤ 500ms | [07-observability.md](07-observability.md) 5.2 |
| 全 API | p99 ≤ 1000ms、失敗率 < 1% | 本書(初期値) |
| 画面(通常) | タイムライン初期表示 ≤ 1 秒(ログイン処理の BCrypt 分 +1.5 秒を許容)、LCP ≤ 2.5s、CLS ≤ 0.1 | 本書(初期値) |
| 画面(CPU 4x・Fast 4G 相当) | 初期表示 ≤ 3 秒、LCP ≤ 4s | 本書(初期値) |
| Lighthouse | LCP ≤ 2.5s、TBT ≤ 300ms、CLS ≤ 0.1(スコアは参考値) | 本書(初期値) |

ローカル環境(Windows + Docker)の値は**絶対評価ではなく、変更前後の比較用**。閾値は初回の結果を見て見直す。

## 2. 構成

| 層 | ツール | 場所 |
|---|---|---|
| API 負荷 | k6(Docker の `grafana/k6`。インストール不要) | `perf/k6/` |
| ブラウザ計測 | Playwright(Chromium)+ Lighthouse | `perf/browser/`(`frontend/` の依存とは分離) |
| データ | 専用 DB `raisetimeline_perf` + `generate_series` のシード | `perf/seed/` |
| 実行入口 | `scripts/perf.ps1` | |
| 実行用設定 | `perf` プロファイル(SQL の DEBUG ログ無し、プール最大 10 を明示) | `backend/src/main/resources/application-perf.properties` |

- 対象は実アプリ(jar)+ docker-compose の PostgreSQL。フロントは**本番ビルド**(`vite preview`、5173)で測る(開発サーバーは HMR 等で値が歪むため対象外)。
- 専用 DB なので開発データには触れない。画像のアップロード(presign は 30 回/分で 429)は対象外のため、S3 の後始末は不要。

### k6 シナリオ

| シナリオ | 内容 |
|---|---|
| `smoke` | 1 VU で主要 API を 1 回ずつ(動作確認) |
| `read-mix` | 閲覧中心の混合(タイムライン following/all をカーソルで 3 ページ、投稿詳細、コメント、プロフィール、検索、いいね付け外し) |
| `write-mix` | 投稿作成→コメント→いいね→フォロー→投稿削除 |
| `login` | ログインのみ(BCrypt の CPU 負荷を別枠で見る) |

負荷の形(`-LoadProfile`): `load`(15 VU 5 分)/ `stress`(〜100 VU)/ `soak`(10 VU 30 分)。

### ブラウザ計測

`measure.mjs`: ログイン→タイムライン初期表示→無限スクロール(100 件)→「全体」タブ→投稿詳細→プロフィール→検索。画面ごとの表示時間、LCP / CLS / ロングタスク / INP 相当、リクエスト数、100 件表示後の JS ヒープと DOM ノード数を `browser.json` に出す。通常と、CPU 4x・Fast 4G 相当(`browser-throttled.json`)の 2 回。
`lighthouse.mjs`: ログイン後のタイムラインとログイン画面(`lighthouse.json`)。

## 3. 実行手順

前提: Docker の `raisetimeline-postgres` が healthy、`.env` が設定済み、Node.js / JDK が入っていること。

```powershell
# API 負荷
scripts\perf.ps1 -Target api -Scenario smoke
scripts\perf.ps1 -Target api -Scenario read-mix -LoadProfile load
# ブラウザ計測(Lighthouse を含む)
scripts\perf.ps1 -Target browser
# API 負荷 + ブラウザ(サーバーが忙しいときの画面の体感)
scripts\perf.ps1 -Target combined
# 件数を 10 倍にして比較
scripts\perf.ps1 -Target all -Rows 50000
```

主なオプション: `-Rows`(投稿数。既定 5000)、`-Users`(既定 50)、`-KeepData`(終了時に DB を残す)、`-Cleanup`(計測せず後始末だけ)、`-Prune`(結果を残す世代数。既定 10)。
閾値を満たさないと終了コード 1 になる。初回はブラウザ(Chromium 約 200MB)の取得に時間がかかる。

スクリプトは CLAUDE.md の「動作確認でのサーバー起動」に従う。8080(browser では 5173 も)が使用中なら、**そのプロセスを停止して既定ポートで起動**し、停止したプロセスを実行の最後に表示する(別ポートへは逃げない)。

## 4. 後始末

`scripts/perf.ps1` は `try/finally` で、成功・失敗・Ctrl+C のいずれでも次を行う。

1. 起動した jar・`vite preview`・k6 のプロセスを停止し、使ったポートの解放を確認する。
2. 専用 DB `raisetimeline_perf` を `DROP DATABASE ... WITH (FORCE)` する(`-KeepData` のときは残す)。DB 名が `*_perf` で終わらないと処理を拒否する(開発 DB の誤削除防止。`seed.sql` / `cleanup.sql` も同様)。
3. コンテナ内のシード一時ファイルと `perf/browser/.tmp`(Chromium のプロファイル等)を削除する。
4. 結果(`perf/results/<日時>/`)は新しい `-Prune` 件だけ残す(Git 管理外)。

`raisetimeline-postgres` / `raisetimeline-s3` のコンテナは停止しない。`-KeepData` で残したあとは `scripts\perf.ps1 -Cleanup` で削除する。

## 5. 結果の読み方

- `perf/results/<日時>/` の `k6-*.json`(k6 の集計)、`browser*.json`、`lighthouse.json`、`app.log`(JSON ログ。`http.route` と `duration_ms` でサーバー側のレイテンシを集計できる)。
- k6 の閾値は `perf/k6/lib/common.js`、ブラウザの閾値は `perf/browser/measure.mjs` の `LIMITS` にある。
- 遅いときの調査: `EXPLAIN (ANALYZE, BUFFERS)` で次を確認する。
  - `PostMapper.findTimeline` の `scope=following`(`OR ... IN (サブクエリ)`)
  - `PostCardMapper` の `COUNT(*) GROUP BY`(いいね数・コメント数)
  - `UserMapper.findProfile` の相関サブクエリ
  - ユーザー検索の trgm インデックス
  接続プール(Hikari 最大 10)の枯渇は、`app.log` の遅延と k6 の VU 数の関係で推測する(Micrometer のメトリクス公開は未実施。07-observability.md 7 章)。

## 6. ベースライン

初回実行後に記録する(ローカル値。比較用)。

| 日付 | 条件 | 結果 |
|---|---|---|
| 2026-10-29 | 投稿 5,000 件・ユーザー 50 人、`combined`(read-mix 15 VU 5 分を背景に) | k6: 4,024 リクエスト、失敗 0%、全体 p95 12.9ms。timeline(all / following)p95 約 13ms。ブラウザ: ログイン→タイムライン 260ms、100 件スクロール 151ms、タイムラインまでの LCP 108ms |
| 2026-10-29 | 投稿 50,000 件・ユーザー 50 人、`all`(read-mix 15 VU 5 分 → ブラウザ → Lighthouse) | k6: 4,045 リクエスト、失敗 0%、全体 p95 13.7ms。timeline p95 約 14ms。ブラウザ(通常): ログイン→タイムライン 235ms、LCP 116ms。CPU 4x・Fast 4G 相当: 同 931ms、LCP 1,268ms。100 件表示後 JS ヒープ約 8.6MB・DOM 3,055 ノード。Lighthouse: timeline 性能 0.98(LCP 1.06s、TBT 0)、ログイン画面 0.95 |

内訳と限界は [初回レポート](10-performance-report-initial.md) を参照。

所見: 学習規模(数千件)とその 10 倍(5 万件)で、どちらも基準に対して大きな余裕がある。件数が 10 倍でも p95 はほぼ変わらず、現状のインデックスとクエリ構成(カーソル方式)が効いている。ユーザー数・同時接続数を増やした `stress` は未実施。
