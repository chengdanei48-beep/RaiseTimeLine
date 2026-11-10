# E2E テスト

画面を通した機能の回帰テスト。`e2e/`(Playwright Test、Chromium のみ)。手動実行に加え、PR ごとに CI でも実行する。
操作ごとの時間計測(ブラウザ性能)は `e2e/tests/perf/` に別枠で置き、**CI には含めず手動のみ**で実行する。大量データでの負荷は [09-performance-testing.md](09-performance-testing.md)(`perf/browser/`)が担当するため、役割はこう分ける。

| | 対象 | データ量 | 実行 |
|---|---|---|---|
| `perf/browser/`(09) | ページ全体の体感(LCP 等) | 大量(5,000〜50,000件) | 手動のみ |
| `e2e/tests/perf/`(本書) | 操作ごとの応答時間(ログイン→表示、投稿送信、いいね等) | 少量(テストが作る分だけ) | 手動のみ |
| `e2e/tests/*.spec.ts`(本書) | 機能の回帰(シナリオ) | 少量 | 手動 + CI(PR・main push) |

## 1. ツール選定

**Playwright Test** を採用。理由:
- 新着バナー・他人のいいね反映など、**2 ユーザーの同時操作**の検証に複数の `BrowserContext` が要る。
- `page.clock`(30 秒ポーリングを実時間で待たない)、`page.route`(401/429/通信失敗の再現)、`page.on('dialog')`(`window.confirm`)、`context.request`(API での前提データ作成)が標準で使える。
- [性能テスト](09-performance-testing.md)の `perf/browser/` と同じ道具(CDP・`PerformanceObserver`)を使え、知見を再利用できる。

不採用: Cypress(複数コンテキストが不得手)、Selenium/WebdriverIO(待機処理が増えて重い)、TestCafe(更新が鈍い)。

## 2. 構成

```
e2e/
  playwright.config.ts   Chromium のみ、@perf は既定で除外(E2E_INCLUDE_PERF=1 で含める)
  fixtures/
    data.ts     一意なユーザー名・本文の生成、画像バッファ(PNG をメモリ上で生成)
    test.ts     登録・ログイン済みの user / secondUser フィクスチャ、コンソールエラー監視
    vitals.ts   Web Vitals 収集・操作時間の計測(perf 用)
  pages/        画面ごとの薄いヘルパー(role / label / aria で要素を取る。data-testid は使わない)
  tests/        シナリオ(機能定義書 F-AUTH〜F-PROF の受け入れ条件に対応)
  tests/perf/   ブラウザ性能テスト(@perf タグ)
scripts/e2e.ps1 起動・実行・後始末(scripts/perf.ps1 の骨格を再利用)
backend/src/main/resources/application-e2e.properties
```

`frontend/` の依存・oxlint 対象には混ぜない(独立パッケージ。`npm run typecheck` で型検査)。

## 3. テストデータの方針

- 認証の前提は **API で作る**(`context.request.post('/api/auth/register')`)。UI を毎回通さない。
- ユーザーIDは `e` + 乱数(15 文字以内)、本文は `e2e-<ラベル>-<時刻>-<乱数>` で一意にする。「全体」タブや検索には他テストのデータが混ざるため、検証は自分が作った一意な文字列で絞る。
- **storageState は共有しない**。アクセストークンはメモリ保持で、Cookie(refresh トークン)しか保存できない上、refresh はローテーションされ、使用済みトークンの再提示は 10 秒の猶予を超えると全トークン失効になる。ユーザーごと・テストごとに新規登録する。
- 画像は `fixtures/data.ts` の `tinyPng()` / `dummyFile()` でメモリ上に作る。フィクスチャファイルは置かない。

## 4. 画像(実 S3Mock)

実行前に確認: S3Mock(`adobe/s3mock:latest`)は `http://localhost:5173` → `http://localhost:9000` のクロスオリジン PUT/GET を**許可している**(2026-11-10 確認。プリフライトに `Access-Control-Allow-Origin` が返る)。署名付き URL・CORS・`<img>` 表示まで実物で検証できる。

- `tests/image.spec.ts` は実 S3Mock を通す(`<img>` の `naturalWidth > 0` まで確認)。
- 429・PUT 失敗などの障害系は `page.route` によるスタブ版で確認する(高速・独立)。
- 専用バケット `raisetimeline-e2e` を実行前に作成し、終了時に中身ごと削除する(`scripts/e2e.ps1`)。

## 5. 実行手順

前提: Docker の `raisetimeline-postgres`(healthy)・`raisetimeline-s3` が起動済み、`.env` 設定済み。

```powershell
scripts\e2e.ps1                        # 全シナリオ
scripts\e2e.ps1 -Grep "F-LIKE"          # 一部だけ
scripts\e2e.ps1 -Headed                # ブラウザを表示(デバッグ)
scripts\e2e.ps1 -Tag perf              # ブラウザ性能計測のみ(手動専用)
scripts\e2e.ps1 -Cleanup               # 実行せず後始末だけ
```

失敗時は `e2e/playwright-report/index.html` にトレース・動画・スクリーンショットが残る。

## 6. 後始末

`scripts/e2e.ps1` は `try/finally` で、成功・失敗・Ctrl+C のいずれでも次を行う。

1. 起動した jar・`vite preview` を停止し、8080/5173 の解放を確認する。
2. 専用 DB `raisetimeline_e2e` を削除する(`-KeepData` で残せる)。DB 名が `*_e2e` で終わらないと処理を拒否する(開発 DB の誤削除防止)。
3. 専用バケット `raisetimeline-e2e` のオブジェクトとバケット自体を削除する。バケット名が `*-e2e` で終わらないと拒否する。
4. `raisetimeline-postgres` / `raisetimeline-s3` のコンテナは停止しない(CLAUDE.md ルール 7)。

## 7. CI

`.github/workflows/ci.yml` の `e2e` ジョブ。PR・main への push で実行し、Postgres は `services:`、S3Mock は `docker run`、バックエンドは jar、フロントは `vite preview`。`@perf` は `playwright.config.ts` の `grepInvert` で既定除外(CI では実行しない)。失敗時は Playwright レポートを artifact として保存する。

## 8. カバレッジ(シナリオ以外に追加したもの)

機能ごとのシナリオ(認証・投稿・タイムライン・コメント・いいね・フォロー・検索・プロフィール・画像)に加え:

- **2 ユーザーの同時操作**: 新着バナー、他人のいいね・フォローの反映
- **障害系**: `route.abort` / 500 / 429 でのロールバック・エラー表示
- **要件の差別化点**: インプレッション数・リポスト UI が出ないことの確認
- **アクセシビリティ**: `@axe-core/playwright` による主要画面(ホーム・ログイン・投稿モーダル)の自動検査、Esc でのモーダル終了
- **レスポンシブ/表示**: 375px 幅での横スクロール無し・モーダルの収まり、ダークモードでクラッシュしないこと
- **セッション**: refresh Cookie 削除後の挙動、API 401 + refresh 401 でのログイン画面への遷移

## 9. 既知の制約

- ワーカー数は CI で 2 に抑えている(ログインの BCrypt が CPU を奪い合うため)。
- 新着バナーのテストは `page.clock` でポーリングの 30 秒を進めて検証する(実時間を待たない)。
- `@axe-core/playwright` は重大度 critical/serious のみを失敗条件にしている(minor/moderate は対象外)。
