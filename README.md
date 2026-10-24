# RaiseTimeLine

X(旧Twitter)のような、テキスト中心のタイムライン型 SNS です。学習目的で、要件定義から設計・実装まで一通り作ります。ユーザー登録・ログインをして短文(と画像)を投稿し、他のユーザーの投稿に**コメント**や**いいね**ができます。ユーザーを**検索**して**フォロー**もできます。

**X との違い(差別化ポイント)**

| 項目 | X / Twitter | RaiseTimeLine |
|---|---|---|
| インプレッション(閲覧)数 | 表示あり | **表示しない・記録もしない** |
| リツイート / 引用 | あり | **提供しない** |
| いいね数・コメント数 | 表示あり | 表示あり |
| フォロー数・フォロワー数 | 表示あり | 表示あり |

- 個人や受講者が使う学習用アプリですが、**複数のユーザーが同時に使う前提**で設計します。第三者が他人の投稿にいいね・コメントできるため、投稿ごとに**いいね数・コメント数**を全ユーザーに表示します。
- 画像は **AWS S3**(非公開バケット)に保存し、署名付き URL でブラウザから直接アップロード・表示します。
- 現在は**実装の途中**です。認証・投稿(テキスト)・コメント・いいね・フォロー・ユーザー検索・プロフィール表示・「フォロー中」「全体」のタイムラインは実装済みで、**投稿画像・プロフィール画像(S3)・プロフィール編集は未実装**です。詳細な前提・制約は [要件定義書](docs/01-requirements.md) を参照してください。

## 主な機能

| 区分 | 内容 | 機能定義書 |
|---|---|---|
| アカウント | ユーザーID(3〜15文字の半角英数字と `_`、大文字小文字を区別せず重複不可)・表示名・パスワード(8〜72文字)で登録・ログイン・ログアウト。パスワードは BCrypt でハッシュ化 | [F-AUTH](docs/features/F-AUTH.md) |
| タイムライン | 「フォロー中」(自分+フォロー中)と「全体」の2タブ。新しい順、20件ずつ。投稿ごとにいいね数・コメント数を表示 | [F-TL](docs/features/F-TL.md) |
| 投稿 | 本文 1〜280文字+画像 最大4枚(各5MB以下)。自分の投稿の削除(編集は不可) | [F-POST](docs/features/F-POST.md) |
| コメント | 投稿へのコメント(1〜280文字)・自分のコメントの削除。コメント数の表示 | [F-CMT](docs/features/F-CMT.md) |
| いいね | いいね・取り消し(1ユーザー1投稿1回、冪等)。いいね数と自分の状態を表示 | [F-LIKE](docs/features/F-LIKE.md) |
| フォロー | フォロー・解除(承認不要)、フォロー中/フォロワー一覧と件数 | [F-FOLLOW](docs/features/F-FOLLOW.md) |
| ユーザー検索 | ユーザーID・表示名の部分一致。結果から直接フォロー、または投稿者名・コメント者名→プロフィール→フォロー | [F-SEARCH](docs/features/F-SEARCH.md) |
| プロフィール | アイコン・表示名・自己紹介・投稿数・フォロー数・フォロワー数、本人の投稿一覧。本人のみ編集可 | [F-PROF](docs/features/F-PROF.md) |

機能の一覧は [機能一覧](docs/02-features.md)、画面は [画面設計](docs/03-screens.md) を参照してください。

**対象外**: リツイート・引用、インプレッション数、投稿・コメントの編集、鍵アカウント・ブロック・ミュート、通知、DM、ハッシュタグ、動画、投稿本文の検索、パスワード再設定、退会、スマートフォン向けレイアウト(詳細は要件定義書 10章)。

## 技術スタック

同階層の TaskManagement と同じバージョンに合わせています。

| 区分 | 技術 | バージョン |
|---|---|---|
| バックエンド | Java / Spring Boot(Web MVC・MyBatis・Security・Actuator) | JDK 25 / Spring Boot 4.1.1 |
| ビルド | Maven(Maven Wrapper) | 3.9.16 |
| データベース | PostgreSQL(開発は Docker、AWS では RDS)/ マイグレーションは Flyway。検索に `pg_trgm` を使用 | 17 |
| 認証 | Spring Security(アクセストークン JWT / `Authorization: Bearer` + リフレッシュトークン HttpOnly Cookie)+ BCrypt。CSRF は無効(Cookie は SameSite=Strict で認証 API のパスに限定) | Spring Boot 管理バージョン |
| フロントエンド | React / TypeScript / Vite / React Router | React 19.3.0 / TypeScript 7.0.2 / Vite 8.3.2 / React Router 7.18.4 |
| Lint(フロントエンド) | Oxlint | 1.86.0 |
| 静的解析(バックエンド) | Spotless・Checkstyle・SpotBugs | 2.46.1 / 10.26.1 / 4.9.8 |
| 実行基盤(フロントのビルド) | Node.js / npm | v24.21.0 / 11.19.0 |
| 画像ストレージ | AWS S3(署名付き URL) | - |
| インフラ(想定) | AWS(EC2 + RDS + ALB + S3)、東京リージョン | 構築するかは未決 |
| ソース管理 | Git / GitHub | Git 2.55.0(開発環境) |

バージョンの一覧と補足は [要件定義書 6.2](docs/01-requirements.md) にあります。TaskManagement からの意図的な変更点(ユーザーIDの大文字小文字を区別しない、ALB を使う等)は 6.3 にまとめています。

## ディレクトリ構成

現在は `docs/`・開発ルール・`backend/`・`frontend/`・`mockup/`(プロトタイプ)です。`infra/` は予定です。

```
RaiseTimeLine/
├─ docs/                      … 設計ドキュメント(下記)
│  ├─ 01-requirements.md      … 要件定義書
│  ├─ 02-features.md          … 機能一覧
│  ├─ features/               … 機能定義書(F-AUTH / F-TL / F-POST / F-CMT / F-LIKE / F-FOLLOW / F-SEARCH / F-PROF)
│  ├─ 03-screens.md           … 画面設計・画面遷移図
│  ├─ 04-er-diagram.md        … ER図・テーブル定義
│  └─ 05-infrastructure.md    … AWS 構成図
├─ .claude/                   … Claude Code の設定とスキル(quality-review)
├─ CLAUDE.md                  … 開発ルール(Claude Code 向け)
├─ backend/                   … バックエンド(Spring Boot + MyBatis。SQL は src/main/resources/mapper/*.xml)
├─ frontend/                  … フロントエンド(React + Vite)
├─ mockup/                    … 画面のプロトタイプ(ブラウザのメモリだけで動くモック。見た目・操作の参考)
├─ docker-compose.yml         … 開発用 PostgreSQL(raisetimeline-postgres、ポート 5433)
├─ .env.example               … 環境変数の雛形(.env にコピーして使う。.env は git 管理外)
└─ infra/terraform/ (予定)    … AWS 構築コード(構築すると決めた場合)
```

## バックエンドの起動(開発)

```powershell
Copy-Item .env.example .env        # DB_PASSWORD と JWT_SECRET(32 バイト以上)を設定する
docker compose up -d               # PostgreSQL(5433)と、画像用の S3 互換サーバー(9000)
cd backend
./mvnw spring-boot:run             # http://localhost:8080(ヘルスチェック: /api/health)
./mvnw verify                      # テスト + Spotless + Checkstyle + SpotBugs(Docker の起動が必要)
```

アプリは起動時にリポジトリ直下の `.env` を読みます(OS の環境変数が優先)。

## フロントエンドの起動(開発)

```powershell
cd frontend
npm install
npm run dev                        # http://localhost:5173(/api は 8080 のバックエンドへ中継)
npm run lint; npm run typecheck; npm run build
```

> テスト(`./mvnw verify`)は Testcontainers が起動する専用の PostgreSQL で動き、開発用 DB(5433)には触れません。Docker が起動していれば足り、開発用 DB の起動は不要です。

## ドキュメント

| ファイル | 内容 |
|---|---|
| [docs/01-requirements.md](docs/01-requirements.md) | 要件定義書(目的・スコープ・業務ルール・非機能要件・技術スタック・API 一覧・エラー応答・対象外) |
| [docs/02-features.md](docs/02-features.md) | 機能一覧(機能 × 画面 × テーブルの対応表つき) |
| [docs/features/](docs/features/) | 機能定義書(入出力と検証・業務ルール・フロー・異常系・受入条件。機能ごと) |
| [docs/03-screens.md](docs/03-screens.md) | 画面一覧(S-01〜S-08)・画面遷移図・ワイヤー・入力検証 |
| [docs/04-er-diagram.md](docs/04-er-diagram.md) | ER図・テーブル定義(users / posts / post_images / comments / likes / follows)・インデックス・クエリ方針 |
| [docs/05-infrastructure.md](docs/05-infrastructure.md) | AWS 構成図(ALB → EC2 → RDS、画像用 S3)・セキュリティグループ・費用の目安・未確定事項 |
| [CLAUDE.md](CLAUDE.md) | 開発フロー・ポート規約などの開発ルール |

## 画面

| ID | 画面 | パス |
|---|---|---|
| S-01 | 新規登録 | `/signup` |
| S-02 | ログイン | `/login` |
| S-03 | タイムライン(フォロー中 / 全体) | `/` |
| S-04 | 投稿詳細(コメント) | `/posts/:id` |
| S-05 | プロフィール | `/users/:id` |
| S-06 | プロフィール編集 | `/settings/profile` |
| S-07 | ユーザー検索 | `/search` |
| S-08 | フォロー中・フォロワー一覧 | `/users/:id/following` `/users/:id/followers` |

## データ設計

テーブルは `users` / `posts` / `post_images` / `comments` / `likes` / `follows` の6つです。投稿とコメントは論理削除(`deleted_at`)、いいね・フォローは複合主キーで重複を防ぎます。インプレッション数・リポスト用のテーブルやカラムは作りません。詳細は [ER図](docs/04-er-diagram.md) を参照してください。

## ローカル起動手順(開発時・実装後)

> アプリの実装後に有効になる手順です。TaskManagement と同じ運用を想定しています。

前提: JDK 25、Node.js、Docker がインストールされていること。

1. DB: `.env.example` を `.env` にコピーし、`docker compose up -d`
2. バックエンド: `cd backend && ./mvnw spring-boot:run`(http://localhost:8080)
3. フロントエンド: `cd frontend && npm install && npm run dev`(http://localhost:5173)

フロントエンドの `/api` へのリクエストは、Vite のプロキシでバックエンド(8080)に転送されます。

### 既定ポート

動作確認では、次の既定ポートを使います(ポートが競合したら、別ポートに逃げず、使っているプロセスを止めてから起動します。詳細は [CLAUDE.md](CLAUDE.md))。

| 対象 | ポート |
|---|---|
| バックエンド | 8080 |
| フロントエンド(Vite) | 5173(固定) |
| PostgreSQL(Docker) | 5433 |

### 環境変数

| 環境変数 | 意味 | 既定値 |
|---|---|---|
| `DB_PORT` | PostgreSQL のポート | `5433` |
| `DB_NAME` | データベース名 | `raisetimeline` |
| `DB_USER` | DBユーザー名 | `raisetimeline` |
| `DB_PASSWORD` | DBパスワード | 開発用の値(`.env.example` 参照) |
| `SERVER_ADDRESS` | 待ち受けアドレス | `127.0.0.1`(AWS では `0.0.0.0`) |
| `APP_S3_BUCKET` / `AWS_REGION` | 画像用 S3 バケット名 / リージョン | AWS 利用時に設定(`ap-northeast-1`) |

AWS 上での設定値は [インフラ構成](docs/05-infrastructure.md) 5章を参照してください。

## テスト・Lint(実装後)

```
cd backend && ./mvnw verify      # バックエンド: 統合テスト(Testcontainers の PostgreSQL。Docker が必要)+ Spotless・Checkstyle・SpotBugs
cd backend && ./mvnw spotless:apply  # 整形の自動修正
cd frontend && npm run lint      # フロントエンドの静的解析(Oxlint)
cd frontend && npm run typecheck # 型チェック
cd frontend && npm run build     # 型チェック + ビルド
```

同じチェックを GitHub Actions(`.github/workflows/ci.yml`)が、PR と main への push のたびに自動で実行します(`backend` / `frontend` の 2 ジョブ)。

品質レビューの観点は `.claude/skills/quality-review/SKILL.md`(Claude Code のスキル `quality-review`)にまとめています。

## API

すべて JSON。`/api/auth/register`・`/api/auth/login`・`/api/auth/refresh`・`/api/auth/logout`・`/api/health` 以外は、ログインして得たアクセストークン(15 分)を `Authorization: Bearer <token>` ヘッダで送る必要があります。リフレッシュトークン(7 日)は HttpOnly Cookie で受け渡します。

| メソッド | パス | 内容 |
|---|---|---|
| POST | `/api/auth/register` | アカウント登録(登録後はログイン状態) |
| POST | `/api/auth/login` | ログイン |
| POST | `/api/auth/refresh` | アクセストークンの更新(リフレッシュトークンの Cookie を使用) |
| POST | `/api/auth/logout` | ログアウト(リフレッシュトークンを失効) |
| GET | `/api/auth/me` | ログイン中のユーザーを取得 |
| GET | `/api/timeline` | タイムライン(`scope`=`following`/`all`、`cursor`、`limit`) |
| POST | `/api/posts` | 投稿の作成(`body`、`imageKeys`) |
| GET / DELETE | `/api/posts/{id}` | 投稿の詳細 / 削除(本人のみ) |
| POST | `/api/uploads/presign` | 画像アップロード用の署名付き URL を発行 |
| GET / POST | `/api/posts/{id}/comments` | コメントの一覧 / 投稿 |
| DELETE | `/api/comments/{id}` | コメントの削除(本人のみ) |
| PUT / DELETE | `/api/posts/{id}/like` | いいね / 取り消し(冪等) |
| GET | `/api/users` | ユーザー検索(`q`、`cursor`、`limit`) |
| GET | `/api/users/{id}` | プロフィール(件数・フォロー済みを含む) |
| PUT | `/api/users/me` | プロフィール更新 |
| GET | `/api/users/{id}/posts` | そのユーザーの投稿一覧 |
| PUT / DELETE | `/api/users/{id}/follow` | フォロー / 解除(冪等) |
| GET | `/api/users/{id}/following` `/followers` | フォロー中 / フォロワー一覧 |
| GET | `/api/health` | ヘルスチェック(ALB が使用) |

エラーは HTTP ステータス(400 入力誤り / 401 未ログイン・認証失敗 / 403 権限なし / 404 対象なし / 409 ユーザーIDの重複)と、`detail` を含む JSON(ProblemDetail)で返します。詳細は要件定義書 7章、各 API の入出力は機能定義書を参照してください。

## インフラ(AWS)

**AWS 上にサーバーを構築するかは未決**ですが、前提とする構成を先に [構成図](docs/05-infrastructure.md) にしています。

```
ブラウザ ─HTTPS→ ALB ─8080→ EC2(Spring Boot + React 同梱) ─5432(SSL)→ RDS PostgreSQL 17
   └──署名付きURLで画像を直接 PUT/GET──→ S3(非公開バケット)
```

- ALB で HTTPS を終端するには、独自ドメインと ACM 証明書が必要です(未定)。
- ALB は常時課金です(全体で月 $60 前後の目安)。使わない間は消す運用を想定します。
- EC2 を複数台にする場合は、JWT の署名鍵(`JWT_SECRET`)を全台で共有します。

## 開発の進め方

1. 作業前に GitHub Issue を作成する(`gh issue create`)
2. `種別/issue番号-概要` の専用ブランチを作る(例: `feature/12-add-login`)
3. main へ直接 push せず、PR(本文に `Closes #12` など)経由でマージする。マージ後、作業ブランチは自動で削除される

詳細は [CLAUDE.md](CLAUDE.md) を参照してください。
