# RaiseTimeLine 要件定義書

| 項目 | 内容 |
|---|---|
| 文書バージョン | 1.0 |
| 作成日 | 2026-10-16 |
| 作成者 | RaiseTimeLine 開発者 |
| 提供形態 | 学習目的(個人・受講者による利用) |

## 改訂履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1 | 2026-10 | 初版(要件・機能一覧・画面設計・ER図のたたき台) |
| 0.2 | 2026-10 | DB を PostgreSQL に確定。画像保存を AWS S3 に変更。プロフィール機能を追加 |
| 1.0 | 2026-10-16 | 同階層の TaskManagement に合わせて文書構成と技術スタックを確定。**フォロー/フォロワー機能とユーザー検索を追加**(従来は対象外)。ログインIDをメールアドレスから**ユーザーID**に変更。タイムラインを「フォロー中/全体」の2タブ構成に変更。機能一覧を [02-features.md](02-features.md)、機能ごとの詳細を [features/](features/) に分離。AWS 構成図 [05-infrastructure.md](05-infrastructure.md) を追加 |

関連文書: [02 機能一覧](02-features.md) / [features/ 機能定義書](features/) / [03 画面設計](03-screens.md) / [04 ER図](04-er-diagram.md) / [05 インフラ構成](05-infrastructure.md)

---

## 1. はじめに

### 1.1 この文書の目的
X(旧Twitter)のようなテキスト中心のタイムライン型 SNS「RaiseTimeLine」について、**何を作り、何を作らないか**、および技術・構成の前提を定める。実装前に認識をそろえるための文書であり、各機能の詳細は機能定義書([features/](features/))に記載する。

### 1.2 アプリの概要
ユーザー登録・ログインをして、短いテキスト(と画像)を投稿し、他のユーザーの投稿に**コメント**や**いいね**ができる SNS。ユーザーを**検索**して**フォロー**でき、タイムラインでは「フォロー中」と「全体」を切り替えて見られる。

個人または受講者が使うが、**複数のユーザーが同時に使う前提**で設計する。第三者が他人の投稿にいいね・コメントできるため、**いいね数・コメント数**は投稿ごとに全ユーザーへ表示する。

### 1.3 差別化ポイント
| 項目 | X / Twitter | 本アプリ |
|---|---|---|
| インプレッション(閲覧)数 | 表示あり | **表示しない・記録もしない** |
| リツイート / 引用 | あり | **提供しない** |
| いいね数・コメント数 | 表示あり | 表示あり |
| フォロー数・フォロワー数 | 表示あり | 表示あり |

数字で競わせる要素のうち、インプレッションとリポストを除くことで、投稿そのものと、コメント・いいねのやり取りに焦点を当てる。

### 1.4 想定する利用者・利用シーン
- **利用者**: 学習者本人、および同じ環境を使う受講者(少人数〜数十人程度)。
- **利用シーン**: 短文の投稿、コメントでの交流、いいね、気になるユーザーの検索とフォロー。
- **前提**: インターネット上のサーバー(AWS)で動かす想定。ただし**構築するかは未決**(5章・[05-infrastructure.md](05-infrastructure.md)参照)。

---

## 2. 提供条件

| 項目 | 内容 |
|---|---|
| 費用 | 無償(学習用)。AWS を使う場合の利用料は実施者が負担 |
| 保証 | 現状有姿(as-is)。データの保全は保証しない |
| 利用の範囲 | 学習・検証目的。本番サービスとしての公開は想定しない |
| 開発の進め方 | TaskManagement と同じく、Issue → 専用ブランチ → PR の流れで管理する |

---

## 3. 成果物

### 3.1 ドキュメント
```
docs/
├─ 01-requirements.md        … 本要件定義書
├─ 02-features.md            … 機能一覧(別立て)
├─ features/                 … 機能定義書(機能単位)
│  ├─ F-AUTH.md   アカウント・ログイン
│  ├─ F-TL.md     タイムライン
│  ├─ F-POST.md   投稿(画像を含む)
│  ├─ F-CMT.md    コメント
│  ├─ F-LIKE.md   いいね
│  ├─ F-FOLLOW.md フォロー・フォロワー
│  ├─ F-SEARCH.md ユーザー検索
│  └─ F-PROF.md   プロフィール
├─ 03-screens.md             … 画面設計・画面遷移図
├─ 04-er-diagram.md          … ER図・テーブル定義
└─ 05-infrastructure.md      … AWS 構成図
```

### 3.2 アプリ(実装時)
TaskManagement と同じ構成を想定する。
- `backend/` … Spring Boot(controller / service / repository(MyBatis の Mapper インターフェース。SQL は `resources/mapper/*.xml`)/ entity / dto / security / exception)。テーブルは Flyway で管理
- `frontend/` … React(pages / components / api)。ビルド成果物は backend の静的リソースに同梱し、実行プロセスを 1 つ(jar)にまとめる
- `infra/terraform/` … AWS 構築コード(構築を行うと決めた時点で作成)

---

## 4. 用語集

| 用語 | 意味 |
|---|---|
| 投稿 (Post) | ユーザーが発信するテキスト(+画像) |
| コメント (Comment) | 投稿に対する返信 |
| いいね (Like) | 投稿に対する共感の表明 |
| フォロー (Follow) | 他のユーザーを「フォロー中」にすること。承認は不要 |
| フォロワー | 自分をフォローしているユーザー |
| ユーザーID | `@` 付きで表示されるハンドル。ログインにも使う。重複不可 |
| 表示名 | 画面に表示する名前。重複可 |
| タイムライン | 投稿を新しい順に並べた一覧。「フォロー中」「全体」の2種類 |
| インプレッション | 投稿が閲覧された回数。**本アプリでは扱わない** |
| 署名付きURL | S3 のファイルへ、期限付きで直接アクセスするための URL |

---

## 5. 機能要件

機能の一覧は [02-features.md](02-features.md)、各機能の詳細な仕様(入出力・検証・フロー・エラー)は [features/](features/) の機能定義書を正とする。本章は概要と共通の業務ルールをまとめる。

### 5.1 機能の概要

| 区分 | 機能定義書 | 概要 |
|---|---|---|
| アカウント・ログイン | [F-AUTH](features/F-AUTH.md) | ユーザーID+パスワードで登録・ログイン・ログアウト |
| タイムライン | [F-TL](features/F-TL.md) | 「フォロー中」「全体」の2タブ。新しい順、20件ずつ |
| 投稿 | [F-POST](features/F-POST.md) | テキスト(最大280字)+画像(最大4枚)。自分の投稿の編集・削除 |
| コメント | [F-CMT](features/F-CMT.md) | 投稿へのコメント・削除。コメント数の表示 |
| いいね | [F-LIKE](features/F-LIKE.md) | いいね・取り消し。いいね数の表示 |
| フォロー | [F-FOLLOW](features/F-FOLLOW.md) | フォロー・解除、フォロー中/フォロワー一覧と件数 |
| ユーザー検索 | [F-SEARCH](features/F-SEARCH.md) | ユーザーID・表示名で検索。結果からプロフィール・フォローへ |
| プロフィール | [F-PROF](features/F-PROF.md) | 表示名・自己紹介・アイコン。本人の投稿一覧 |

### 5.2 共通の業務ルール

| ID | ルール |
|---|---|
| BR-01 | 投稿本文は 1〜280 文字(前後の空白は除いて数える)。画像のみの投稿は不可 |
| BR-02 | 1投稿に添付できる画像は最大 4 枚。形式は JPEG/PNG/GIF/WebP、1枚 5MB 以下 |
| BR-03 | コメント本文は 1〜280 文字 |
| BR-04 | 1ユーザーは 1投稿に対していいねを 1 回のみ可能(取り消し可) |
| BR-05 | 投稿の編集・削除、コメントの削除は作成者本人のみ。編集できるのは投稿の本文のみ(BR-01 の規則を適用し、`updated_at` を更新して「編集済み」を表示)。コメントの編集機能は設けない |
| BR-06 | 投稿を削除した場合、その投稿のコメント・いいね・画像も参照不可にする |
| BR-07 | 自分の投稿へのいいね・コメントも許可する |
| BR-08 | いいね数・コメント数は全ログインユーザーに表示する |
| BR-09 | 自己紹介は 0〜160 文字。アイコン画像は 1 枚、BR-02 と同じ形式、2MB 以下 |
| BR-10 | プロフィールの編集は本人のみ。閲覧は全ログインユーザー |
| BR-11 | ユーザーIDは 3〜15 文字の半角英数字と `_`。**大文字小文字を区別せず**重複不可 |
| BR-12 | フォローは承認不要。自分自身はフォローできない。同じ相手への重複フォローは 1 件のまま(冪等) |
| BR-13 | 「フォロー中」タイムラインは、自分とフォロー中のユーザーの投稿を表示する。フォローが 0 人でも自分の投稿は表示する |
| BR-14 | フォロー数・フォロワー数はプロフィールに表示する。**インプレッション数・閲覧数は、どの画面にも表示せず、記録もしない** |
| BR-15 | ユーザー検索は、ログイン中の全ユーザーが行える。結果に自分自身は含めない |

---

## 6. 非機能要件・技術スタック

### 6.1 非機能要件

| 区分 | 要件 |
|---|---|
| セキュリティ | パスワードは BCrypt でハッシュ化。HTTPS 前提(AWS では ALB で終端)。アクセストークンは `Authorization` ヘッダで送り、リフレッシュトークンは HttpOnly・SameSite=Strict の Cookie(Path は `/api/auth` に限定)で受け渡すため、CSRF 対策は追加しない。XSS 対策(出力のエスケープ)、認可チェック(他人の投稿・コメントは削除不可、他人のプロフィールは編集不可)。権限のない操作は 403、存在しない・削除済みの対象は 404 |
| 認証方式 | ユーザーID+パスワード。アクセストークン(JWT, 15 分。`Authorization: Bearer` ヘッダで送り、画面ではメモリにのみ保持)+ リフレッシュトークン(7 日。HttpOnly Cookie。使用のたびに再発行し、使用済みの再提示は全失効)。TaskManagement のセッション方式からの意図的な変更。ID・パスワードを忘れた場合の復旧手段は用意しない |
| 性能 | 一覧は 20 件ずつ取得(カーソル方式)。学習規模(ユーザー数十人・投稿数千件)で、一覧の表示が 1 秒以内を目標とする |
| 整合性 | いいね・フォローの重複登録は DB の複合主キーで防止。冪等な API とする |
| 画像保存 | AWS S3(非公開バケット)。署名付き URL でアップロード・表示。DB にはオブジェクトキーのみ保存 |
| 対応ブラウザ | Google Chrome、Microsoft Edge の最新版(PC)。スマートフォン向けレイアウトは対象外 |
| 言語 | 画面表示はすべて日本語 |
| 保守性 | フロントエンドとバックエンドを分離した REST API 構成。静的解析・テストを CI で実行 |
| 運用・監視 | ログは標準出力へ。`LOG_FORMAT=ecs` で JSON(構造化ログ)にでき、全ログに `request_id` を付け、アクセスログ・認証イベントを出す。パスワード・トークン等はログに出さない。詳細は [07-observability.md](07-observability.md) |

### 6.2 技術スタック
TaskManagement(`../TaskManagment/docs/requirements.md` 6.5)の確定値に合わせる。バージョンはそちらの更新に追従して見直す。

| 区分 | 採用技術 | バージョン | 補足 |
|---|---|---|---|
| バックエンド(言語) | Java(JDK) | 25(LTS) | |
| バックエンド(FW) | Spring Boot | 4.1.1 | ビルドは Maven(Maven Wrapper 3.9.16) |
| データベースアクセス | MyBatis(mybatis-spring-boot-starter) | 4.1.0 | PostgreSQL 用 JDBC ドライバ。SQL は Mapper XML(`resources/mapper/`)に記述する(JPA は使わない) |
| データベース | PostgreSQL | 17(開発: Docker `postgres:17-alpine` / AWS: RDS) | テーブル定義は Flyway。検索用に `pg_trgm` 拡張を使用 |
| 認証 | Spring Security(OAuth2 Resource Server による JWT 検証、HS256)+ BCrypt | Spring Boot 管理バージョン | 署名鍵は環境変数 `JWT_SECRET`。CSRF は無効(Cookie はリフレッシュトークンのみで、SameSite=Strict・Path=`/api/auth`) |
| フロントエンド(言語) | TypeScript | 7.0.2 | |
| フロントエンド(UI) | React / React DOM | 19.3.0 | Next.js は使用しない |
| フロントエンド(ビルド) | Vite(+ `@vitejs/plugin-react`) | 8.3.2(+ 6.1.1) | 開発サーバーは 5173 番固定 |
| 画面遷移 | React Router(`react-router-dom`) | 7.18.4 | |
| 静的解析(フロント) | Oxlint | 1.86.0 | `npm run lint` / `npm run typecheck` |
| 静的解析(バック) | Spotless・Checkstyle・SpotBugs | 2.46.1 / 10.26.1 / 4.9.8 | `./mvnw verify` |
| テスト用 DB | Testcontainers(PostgreSQL) | 2.0.5(PostgreSQL 17) | 開発用 DB と分離。Docker が必要 |
| 実行基盤(フロントのビルド) | Node.js / npm | v24.21.0 / 11.19.0 | jar 同梱ビルドは `frontend-maven-plugin` |
| API 通信 | REST API(JSON)、ブラウザ側は標準の fetch | - | |
| 画像ストレージ | AWS S3 | - | AWS SDK for Java v2(2.57.3)で署名付き URL を発行 |
| 画像ストレージ(開発) | S3Mock(`adobe/s3mock`、Docker) | latest | 開発用の S3 互換サーバー(`docker compose up -d` で起動、ポート 9000)。署名の検証・有効期限の判定はしないので、本番相当の確認は AWS で行う |
| インフラ | AWS(EC2 + RDS + ALB + S3) | リージョン `ap-northeast-1` | 構成は [05-infrastructure.md](05-infrastructure.md)。構築方法(Terraform 等)は未決 |
| ソース管理 | Git / GitHub | - | Issue → 専用ブランチ(`種別/issue番号-概要`)→ PR。master への直接 push 禁止 |

**既定ポート**(TaskManagement と同じ運用): バックエンド 8080 / フロントエンド(Vite)5173 / PostgreSQL(Docker)5433。動作確認時にポートを変えない。

### 6.3 TaskManagement からの意図的な変更点
| 項目 | TaskManagement | 本アプリ | 理由 |
|---|---|---|---|
| ユーザーIDの大文字小文字 | 区別する | **区別しない**(BR-11) | SNS では `@Alice` と `@alice` が別人になると紛らわしく、なりすましにつながるため |
| 公開範囲 | PC 内のみ | インターネット(AWS)を想定 | 複数ユーザーが使う SNS のため |
| ロードバランサー | 使わない(費用) | ALB を使う前提 | 利用者の指定。常時課金が発生する(05 参照) |
| 画像 | なし | S3 | 画像投稿機能のため |
| 認証方式 | セッション(Cookie)+ CSRF | **JWT のアクセストークン**(`Authorization: Bearer`)+ **リフレッシュトークン**(HttpOnly Cookie, DB 管理) | API の認証は状態を持たず、EC2 の複数台化でセッション共有が不要になる。アクセストークンを短命(15 分)にし、ログアウト・盗難検知はリフレッシュトークンの失効で行う(F-AUTH 6章) |
| データベースアクセス | Spring Data JPA | **MyBatis** | SQL を明示的に書いて学ぶため(`lower(username)` 検索や集計など) |

---

## 7. データ設計・API

### 7.1 データ設計
テーブル定義・ER 図は [04-er-diagram.md](04-er-diagram.md) を正とする。テーブルは `users` / `posts` / `post_images` / `comments` / `likes` / `follows` の 6 つ。

### 7.2 API 一覧
> **API 仕様書(OpenAPI)は、コードから自動生成している。** 項目・型・検証・エラー応答の詳細は [docs/api/openapi.yaml](api/openapi.yaml)(実際の API と一致することをテストで検証している)、実際に試すときは開発サーバーの Swagger UI(`http://localhost:8080/swagger-ui.html`。`.env` の `API_DOCS_ENABLED=true` のとき)を見る。下の表は概要。

画面とバックエンドは JSON の REST API で通信する。`/api/auth/register`・`/api/auth/login`・`/api/auth/refresh`・`/api/auth/logout`・`/api/health` 以外は、ログイン(`Authorization: Bearer` ヘッダのアクセストークン)が必要。詳細は各機能定義書を参照。

| メソッド | パス | 内容 | 成功時 | 機能 |
|---|---|---|---|---|
| POST | `/api/auth/register` | アカウント登録(登録後はログイン状態) | 201 | F-AUTH |
| POST | `/api/auth/login` | ログイン | 200 | F-AUTH |
| POST | `/api/auth/refresh` | アクセストークンの更新(リフレッシュトークンの Cookie を使用) | 200 | F-AUTH |
| POST | `/api/auth/logout` | ログアウト(リフレッシュトークンを失効) | 204 | F-AUTH |
| GET | `/api/auth/me` | ログイン中のユーザーを取得 | 200 | F-AUTH |
| GET | `/api/timeline` | タイムライン。`scope`=`following`/`all`、`cursor`、`limit` | 200 | F-TL |
| POST | `/api/posts` | 投稿の作成(`body`、`imageKeys`) | 201 | F-POST |
| GET | `/api/posts/{id}` | 投稿の詳細(件数・自分のいいね済みを含む) | 200 | F-TL |
| PATCH | `/api/posts/{id}` | 投稿の編集(本人のみ。`body` のみ) | 200 | F-POST |
| DELETE | `/api/posts/{id}` | 投稿の削除(本人のみ) | 204 | F-POST |
| POST | `/api/uploads/presign` | 画像アップロード用の署名付き URL を発行(`purpose`=`POST`/`AVATAR`、`contentType`、`size`) | 200 | F-POST / F-PROF |
| GET | `/api/posts/{id}/comments` | コメント一覧(古い順) | 200 | F-CMT |
| POST | `/api/posts/{id}/comments` | コメント投稿(`body`) | 201 | F-CMT |
| DELETE | `/api/comments/{id}` | コメント削除(本人のみ) | 204 | F-CMT |
| PUT | `/api/posts/{id}/like` | いいね(冪等) | 204 | F-LIKE |
| DELETE | `/api/posts/{id}/like` | いいね取り消し(冪等) | 204 | F-LIKE |
| GET | `/api/users` | ユーザー検索。`q`、`cursor`、`limit` | 200 | F-SEARCH |
| GET | `/api/users/{id}` | プロフィール(フォロー数・フォロワー数・フォロー済みを含む)。`me` は使えない(自分は `GET /api/users/me`) | 200 | F-PROF |
| GET | `/api/users/me` | 自分のプロフィール(`GET /api/users/{id}` と同じ形式) | 200 | F-PROF |
| PUT | `/api/users/me` | プロフィール更新(`displayName`、`bio`、`avatarKey`) | 200 | F-PROF |
| GET | `/api/users/{id}/posts` | そのユーザーの投稿一覧 | 200 | F-PROF |
| PUT | `/api/users/{id}/follow` | フォロー(冪等) | 204 | F-FOLLOW |
| DELETE | `/api/users/{id}/follow` | フォロー解除(冪等) | 204 | F-FOLLOW |
| GET | `/api/users/{id}/following` | フォロー中の一覧 | 200 | F-FOLLOW |
| GET | `/api/users/{id}/followers` | フォロワーの一覧 | 200 | F-FOLLOW |
| GET | `/api/health` | ヘルスチェック(ALB が使用) | 200 | 共通 |

### 7.3 エラー応答
HTTP ステータスと `detail`(メッセージ)を含む JSON(RFC 9457 の ProblemDetail 形式)で返す。

| ステータス | 意味 |
|---|---|
| 400 | 入力内容の誤り(必須未入力・文字数超過・画像の形式/サイズ不正・自分自身のフォローなど) |
| 401 | 未ログイン(トークンなし・署名不正・期限切れ。リフレッシュトークンの無効・失効を含む)、またはユーザーID・パスワードの誤り |
| 403 | 権限なし(他人の投稿の削除など) |
| 404 | 対象が存在しない(削除済みの投稿を含む) |
| 409 | 競合(ユーザーIDの重複) |
| 429 | 短時間に操作が多すぎる(署名付き URL の発行は、1 ユーザーあたり 1 分に 30 回まで)。`Retry-After`(秒)で、やり直せるまでの時間を返す |

---

## 8. 画面
画面一覧・遷移図・各画面の仕様は [03-screens.md](03-screens.md) を参照。画面は S-01 新規登録 / S-02 ログイン / S-03 タイムライン / S-04 投稿詳細 / S-05 プロフィール / S-06 プロフィール編集 / S-07 ユーザー検索 / S-08 フォロー中・フォロワー一覧。

---

## 9. 保守・サポート
学習用のため、サポート窓口・サポート期間は設けない。不具合や要望は GitHub の Issue で管理する(TaskManagement と同じ運用)。

---

## 10. 対象外の事項と今後の拡張候補

### 10.1 今回は対応しないこと
| 対象外の事項 | 理由・影響 |
|---|---|
| リツイート(リポスト)・引用投稿 | 差別化方針 |
| インプレッション数・閲覧数の表示/記録 | 差別化方針 |
| コメントの編集 | 削除のみ提供(BR-05)。投稿は本文のみ編集可 |
| 鍵アカウント(フォロー承認制)、ブロック、ミュート | 範囲を絞るため。フォローは誰でも自由に行える |
| 通知、DM、ハッシュタグ、動画投稿 | 範囲を絞るため |
| 投稿本文・コメントの検索 | ユーザー検索のみ対象(F-SEARCH) |
| パスワード再設定、メールアドレスの登録、ソーシャルログイン | メール送信等の外部連携が必要なため |
| ユーザー(アカウント)の削除・退会 | 範囲を絞るため |
| スマートフォン向けレイアウト | PC 利用を前提とするため |
| S3 上の不要オブジェクトの自動削除 | 論理削除のため即時には消さない。将来のバッチ化を検討 |

### 10.2 今後の拡張候補(実施を約束するものではありません)
- 投稿本文・コメントの検索(PostgreSQL の全文検索)
- 通知(フォローされた・いいねされた・コメントされた)
- 鍵アカウント、ブロック、ミュート
- `like_count` などの非正規化による高速化
- CloudFront による画像配信、EC2 の複数台化(JWT はステートレスなので、署名鍵 `JWT_SECRET` を共有すれば足りる)

### 10.3 未確定事項
| 項目 | 現状の扱い |
|---|---|
| AWS 上にサーバーを構築するか | 未決。構成図のみ先に作成([05-infrastructure.md](05-infrastructure.md)) |
| EC2 の台数 | 当面 1 台。ログイン状態は JWT でサーバーに持たないため、複数台にする場合は署名鍵(`JWT_SECRET`)を全台で共有する |
| 独自ドメインと ACM 証明書 | 未定。ALB で HTTPS 終端するには独自ドメインが必要 |
| 構築手段 | TaskManagement と同じ Terraform + AWS CLI を想定(未決) |

---

## 11. 要件の変更について
要件の変更は GitHub の Issue で起票し、本書と関連文書を更新して、改訂履歴に記録する。
