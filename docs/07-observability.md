# 07. ログ運用・監視 設計書

RaiseTimeLine のログの出し方(設計)と、ログを使った運用・監視の方針をまとめる。
実装は「構造化ログ(JSON)」「request_id」「アクセスログ」「認証イベント」まで。ログの集約サービス(CloudWatch Logs / Datadog)への送信と、アラートの設定は、インフラ構築時に 6 章の方針で行う(状態は 9 章)。

## 1. 目的と範囲

| 目的 | 内容 |
|---|---|
| 障害の検知 | エラー率・遅延・停止に気づく |
| 原因の調査 | 1 つのリクエストを、request_id でログ全体から追える |
| セキュリティ | 不正なログイン・トークンの盗難の兆候に気づく |
| 監査 | 誰が・いつ・何をしたかの最低限の記録 |

範囲: バックエンド(Spring Boot)のログ。メトリクス(Actuator / Micrometer)・トレース(APM)は、7 章で方針のみ記す。フロントエンドのエラー収集は対象外。

## 2. ログの設計

### 2.1 出力先と形式

- 出力先は**標準出力のみ**(ファイルに書かない)。ローテーションや収集は実行基盤(systemd / コンテナのログドライバ / CloudWatch エージェント)の役割とする。
- 形式は環境変数 `LOG_FORMAT` で切り替える。

| `LOG_FORMAT` | 形式 | 使う場面 |
|---|---|---|
| (未指定) | 読みやすいテキスト | ローカル開発 |
| `ecs` | JSON(Elastic Common Schema) | 本番・監視サービスへ送る環境(推奨) |
| `logstash` | JSON(Logstash 形式) | ECS を使わない収集基盤 |

仕組みは Spring Boot 標準の構造化ログ(`logging.structured.format.console`)。ライブラリの追加はない。`application.properties` で `LOG_FORMAT` を渡している。

関連する環境変数:

| 変数 | 既定 | 意味 |
|---|---|---|
| `LOG_FORMAT` | (空) | 上記 |
| `APP_ENV` | `dev` | 環境名(`service.environment`)。本番は `prod` |
| `APP_VERSION` | `unknown` | バージョン(`service.version`)。デプロイ時にコミット SHA やタグを渡すと、デプロイ前後の比較ができる |

### 2.2 フィールド

JSON の 1 行が 1 件のログ。主なフィールドは次のとおり。

| フィールド | 例 | 由来 | 備考 |
|---|---|---|---|
| `@timestamp` | `2026-10-26T10:00:00.123Z` | ログ基盤 | UTC |
| `log.level` | `INFO` | ログ基盤 | 2.3 参照 |
| `message` | `GET /api/posts/{id} 404 12ms` | ログ呼び出し | 人が読む文 |
| `log.logger` | `access` | ログ基盤 | アクセスログは `access` |
| `service.name` / `service.version` / `service.environment` | `raisetimeline` / `1.4.2` / `prod` | 設定 | 全ログ共通 |
| `request_id` | `3f2a…` | MDC | そのリクエスト中の全ログに付く |
| `user_id` | `7` | MDC | 認証済みのリクエストのみ |
| `event` | `http_request` | 呼び出し側 | 機械が数える種類(2.4) |
| `http.method` / `http.route` / `http.status` / `duration_ms` | `GET` / `/api/posts/{id}` / `404` / `12` | アクセスログ | |
| `url.path` | `/api/posts/42` | アクセスログ | ルートが決まらないときだけ(2.5) |
| `error.*` | `error.stack_trace` | ログ基盤 | 例外を渡したとき |

### 2.3 ログレベルの基準

| レベル | 基準 | 本番で出すか | 例 |
|---|---|---|---|
| ERROR | **人が対応すべき**失敗。アラートの対象 | 常に | 予期しない例外(500)、5xx のアクセスログ |
| WARN | 想定内だが、増えたら見る異常 | 常に | ログイン失敗、リフレッシュトークンの再利用検知 |
| INFO | 業務上意味のあるイベント | 常に | アクセスログ(4xx を含む)、ログイン成功、ユーザー登録 |
| DEBUG | 調査用 | 通常オフ | SQL(`application-dev.properties` で開発のみ) |

**4xx(利用者の入力ミス・未ログイン・存在しない対象)は ERROR にしない。** ERROR は「サーバー側で直すべきこと」だけにし、アラートのノイズを避ける。

### 2.4 イベント(`event`)の一覧

`event` は、ログから件数を数えたり、アラートを張ったりするための種類名。**名前は変えない**(監視の設定が依存する)。追加は自由。

| `event` | レベル | 出す場所 | 主な付加情報 | 用途 |
|---|---|---|---|---|
| `http_request` | INFO(5xx は ERROR) | `RequestLoggingFilter` | `http.*`, `duration_ms` | 件数・エラー率・遅延 |
| `user_registered` | INFO | `AuthService.register` | `user_id` | 登録数の推移 |
| `login_succeeded` | INFO | `AuthService.login` | `user_id` | ログイン数 |
| `login_failed` | WARN | `AuthService.login` | (なし) | 総当たり攻撃の検知(急増を見る) |
| `refresh_token_reuse_detected` | WARN | `RefreshTokenService.consume` | `user_id` | **トークン盗難の疑い**。1 件でも調査 |

### 2.5 request_id とアクセスログ(`RequestLoggingFilter`)

- 全リクエストに `request_id` を付ける。応答ヘッダー `X-Request-Id` でクライアントにも返す。**利用者から問い合わせがあったとき、この ID でログを引く**。
- クライアントが `X-Request-Id` を送ってきた場合は、`[A-Za-z0-9._-]{1,64}` に合うときだけ採用する。それ以外は捨てて採番する(JSON やログの偽装を防ぐ)。
- アクセスログは 1 リクエスト 1 行。`/api/health` は頻繁で価値が低いため出さない。
- パスは**テンプレート**(`/api/posts/{id}`)で記録する。ID 入りの生のパスだと種類が無限に増え、集計できず、監視サービスの費用も増える。認証で弾かれた等でテンプレートが決まらないときだけ、生のパスを `url.path` に記録する。
- `user_id` は、`UserMdcFilter`(Bearer 認証の直後)が MDC に入れる。認証できなかったリクエストには付かない。

### 2.6 ログに出してはいけないもの

| 種類 | 理由・対応 |
|---|---|
| パスワード(平文・ハッシュ) | リクエスト本文をログに出さない。エンティティの `toString` に含めない |
| アクセストークン・リフレッシュトークン・Cookie | 同上。`RefreshToken.toString()` はハッシュも出さない |
| JWT 署名鍵・DB パスワード・S3 の鍵 | 設定値をログに出さない |
| 署名付き URL | クエリに署名が含まれる |
| ログイン失敗時の入力(ユーザーID・パスワード) | ID の有無の特定(列挙)や、打ち間違えたパスワードの漏えいを防ぐ。`login_failed` には何も付けない |
| メールアドレス・IP・氏名などの個人情報 | 必要になるまで記録しない。ユーザーは `user_id` で識別する |

この方針は**テストで守る**(`StructuredLoggingTest`: 登録・ログイン・リフレッシュの一連の操作の出力に、パスワードとトークンが含まれないことを確認)。新しいログを足すときも、ここに反する情報を入れない。

### 2.7 ログを書くときの規約(開発者向け)

- ロガーは `LoggerFactory.getLogger(クラス)`。`System.out` / `printStackTrace` は使わない。
- 機械が数える情報は、メッセージに埋め込まず**キーと値**で渡す。

```java
LOG.atWarn()
    .addKeyValue("event", "refresh_token_reuse_detected")
    .addKeyValue("user_id", userId)
    .log("失効済みリフレッシュトークンの再提示を検知");
```

- 例外は握りつぶさず、最後の 1 か所で 1 回だけ ERROR で記録する(`GlobalExceptionHandler`)。途中で何度も記録しない(重複する)。
- ループの中や高頻度の処理で INFO を出さない(量と費用が増える)。
- 4xx 系の業務例外は、ハンドラーでは記録しない(アクセスログのステータスで足りる)。

## 3. ローカルで確認する

JSON ログを出して動作を確かめる手順(ポートは CLAUDE.md の既定値)。

```powershell
# backend/ で、JSON 形式で起動する(.env は自動で読まれる)
$env:LOG_FORMAT = "ecs"
./mvnw spring-boot:run
```

別のターミナルで:

```powershell
curl.exe -i http://localhost:8080/api/auth/me          # 401。応答ヘッダーに X-Request-Id
curl.exe -i -H "X-Request-Id: my-test-001" http://localhost:8080/api/auth/me
```

標準出力に 1 リクエスト 1 行の JSON が出る。実際の出力例(整形して掲載):

```json
{"@timestamp":"2026-10-26T04:34:34.797Z",
 "log":{"level":"INFO","logger":"access"},
 "service":{"name":"raisetimeline","version":"68-local","environment":"dev"},
 "message":"POST /api/auth/login 401 157ms",
 "request_id":"0aa6c5ec-c45b-4c6f-997f-9db91e0a595c",
 "event":"http_request",
 "http":{"method":"POST","status":401,"route":"/api/auth/login"},
 "duration_ms":157,
 "ecs":{"version":"8.11"}}
```

ドット付きの名前(`http.status` など)は、JSON では入れ子(`http` → `status`)になる。`request_id` で絞り込める(PowerShell の例):

```powershell
Get-Content be.log | Where-Object { $_ -like '*my-test-001*' }
```

JSON を整形して読むには `jq`(あれば)を使う: `Get-Content be.log | jq .`

## 4. 保持期間とアクセス権

| 種類 | 保持 | 理由 |
|---|---|---|
| 通常ログ(検索できる状態) | 14〜30 日 | 障害調査には足りる。長いほど費用が増える |
| 監査相当(`login_*`、`refresh_token_reuse_detected`、`user_registered`) | 90 日〜1 年(安価な保管先へ) | 不正アクセスの事後調査 |
| 閲覧できる人 | 開発者・運用者に限る | 個人に結びつく情報を含みうる |

学習用の本番では、CloudWatch Logs のロググループに保持期間(例: 30 日)を設定するのが最小の方法。**保持期間を設定しないと無期限に残り、費用が増え続ける**。

## 5. 監視の設計

### 5.1 考え方

1. **症状(利用者への影響)で通知し、原因では通知しない。** 「5xx が増えた」「遅い」は通知する。「CPU 80%」は通知せず、ダッシュボードで見る。
2. **通知が来たら、必ず人が何かする。** 何もしなくてよい通知は消す。
3. **すべてのアラートに対応手順(8 章)を付ける。**
4. 最初は緩く始めて、1〜2 週間の実測で調整する。通知が多すぎると無視されるようになる(アラート疲れ)。
5. 内部の監視だけでなく、**外形監視**(外から `/api/health` を呼ぶ)を 1 つ置く。サーバーごと落ちると、内部からは何も出せない。

### 5.2 目標(SLO)の初期案

| 指標(SLI) | 目標 | 取り方 |
|---|---|---|
| 可用性(5xx でない割合) | 99.5% / 月 | `event=http_request` のうち `http.status<500` の割合 |
| タイムライン API のレイテンシ | p95 500ms 以下 | `http.route=/api/timeline` の `duration_ms` |
| ログインの成功率 | 監視のみ(目標なし) | `login_succeeded` と `login_failed` の比 |

学習用の規模なので、まず可用性だけを SLO とし、他は様子を見る。

### 5.3 アラート一覧(初期案)

| # | 名前 | 条件 | 重要度 | 通知先 | 対応 |
|---|---|---|---|---|---|
| A1 | サービス停止 | 外形監視の `/api/health` が 3 分連続で失敗 | 緊急 | 即時(メール/プッシュ) | 8.1 |
| A2 | 5xx の増加 | `http.status>=500` が 5 分間で 5 件以上、かつ全体の 5% 超 | 緊急 | 即時 | 8.2 |
| A3 | ERROR ログの発生 | `log.level=ERROR` かつ `event` が `http_request` 以外が 5 分間に 1 件以上 | 警告 | Slack/メール | 8.2 |
| A4 | 遅延 | `/api/timeline` の p95 が 500ms 超を 15 分 | 警告 | Slack/メール | 8.3 |
| A5 | ログイン失敗の急増 | `login_failed` が 5 分間に 30 件以上 | 警告 | Slack/メール | 8.4 |
| A6 | トークン再利用の検知 | `refresh_token_reuse_detected` が 1 件以上 | 警告(調査必須) | Slack/メール | 8.5 |
| A7 | 429 の急増 | `http.status=429` が 5 分間に 50 件以上 | 情報 | ダッシュボード | 8.4 |
| A8 | ログが途絶 | 通常時にあるはずのアクセスログが 10 分間ゼロ | 警告 | Slack/メール | 8.1 |

しきい値は初期案。実測の平常値の 2〜3 倍を目安に調整し、変更したら本表を更新する。

重要度の定義:

| 重要度 | 意味 | 想定する対応 |
|---|---|---|
| 緊急 | 利用者が使えない | すぐ対応 |
| 警告 | 放置すると悪化・要調査 | 当日〜翌営業日 |
| 情報 | 傾向の確認 | ダッシュボード/週次で確認 |

### 5.4 ダッシュボード(最小)

「障害時にまず開く 1 枚」を決めて、次を並べる。

- リクエスト数(`http_request` の件数)、エラー率(5xx / 全体)、4xx の件数
- ルート別のレイテンシ(p50 / p95)
- `login_failed` / `login_succeeded` / `refresh_token_reuse_detected` の件数
- デプロイのマーカー(`service.version` の切り替わり)
- 依存先(DB 接続、S3)の状態

## 6. 監視サービスの使い方(Datadog / CloudWatch)

どちらも**JSON ログをそのまま取り込める**(`LOG_FORMAT=ecs`)。選択の目安:

| | CloudWatch Logs + Alarms | Datadog |
|---|---|---|
| 向く規模 | 小さく始める。AWS のみ | 複数サービス・チーム、APM も使う |
| 費用 | 取り込み量と保管量。小規模なら安い | ホスト数 + ログ取り込み量。ログが膨らみやすい |
| 検索 | Logs Insights(クエリ) | 強力な検索・ダッシュボード・SLO |
| 導入 | CloudWatch エージェント(EC2) | Datadog Agent(EC2/コンテナ) |

現状の AWS 構成(`docs/05-infrastructure.md`)では、**まず CloudWatch Logs + メトリクスフィルタ + アラーム + SNS(メール通知)**が最小で、NAT Gateway など高額な資源も要らない。Datadog は、APM で遅い SQL や N+1 を継続的に見たくなった段階で、無料枠・試用から導入する。

### 6.1 Datadog を使う場合の設計

- **統一タグ**: `env` / `service` / `version` をログ・メトリクス・トレースの全部に付ける。`service.environment` / `service.name` / `service.version`(2.2)がこれに対応する。タグが揃うと、ログからトレースへ、デプロイ前後の比較へと辿れる。
- **ログのパース**: JSON なので自動で属性になる。`request_id` / `user_id` / `event` を**ファセット**にすると絞り込みが速い。
- **費用の管理**(ログは取り込み量で課金され、最も膨らみやすい):
  - 除外フィルタで、取り込み不要なログ(DEBUG、ヘルスチェック)を捨てる。ヘルスチェックはアプリ側で既に出していない。
  - 検索用(短期)と、アーカイブ(S3 などの安価な保管先)を分ける。
  - 月次で、取り込み量の多いログのパターンを見直す。
  - タグに `user_id` のような値の種類が多いものを使わない(メトリクスの費用と性能に直結)。ログの属性としては問題ない。
- **モニター**: 5.3 の表をそのままモニターにする。ログモニター(`event:login_failed` の件数)、メトリクスモニター、Synthetics(外形)を使い分ける。
- **SLO**: 5.2 の可用性をログベース(または Synthetics)の SLO として登録し、エラーバジェットの消費速度(バーンレート)でも通知すると、誤報が少ない。

### 6.2 CloudWatch を使う場合の設計

- ロググループ `/raisetimeline/backend`、保持期間 30 日。
- **メトリクスフィルタ**でログから数値を作る。例:
  - 5xx: フィルタパターン `{ $.http.status >= 500 }`(ECS 形式の JSON は `{"http":{"status":500}}` のように入れ子になるため、`$.http.status` で参照できる)
  - `login_failed`: `{ $.event = "login_failed" }`
  - `refresh_token_reuse_detected`: `{ $.event = "refresh_token_reuse_detected" }`
- メトリクスにアラームを付け、SNS トピック経由でメール/Slack に通知する。
- 調査は **Logs Insights** で `request_id` や `event` を条件に検索する。例:

```
fields @timestamp, message, request_id, user_id
| filter request_id = "3f2a…"
| sort @timestamp asc
```

## 7. メトリクス・トレース(今後)

| 種類 | 方針 | 状態 |
|---|---|---|
| メトリクス | Actuator + Micrometer。RED(件数・エラー率・レイテンシ)を取る。JVM・HikariCP は自動で取れる | 未実施(現状は health のみ公開。公開範囲を広げるときは認証・ネットワークで保護する) |
| トレース | Datadog APM / OpenTelemetry。遅い SQL・N+1 の発見に使う | 未実施 |

当面は、ログ(アクセスログの `duration_ms` と `http.status`)から RED の大半を代替できる。

## 8. 対応手順(runbook)

共通の最初の動き: ①影響範囲(いつから・どの API・何割)②直近のデプロイ(`service.version`)③該当リクエストの `request_id` でログを追う。

### 8.1 A1 / A8 サービス停止・ログ途絶
1. `/api/health` を自分で呼ぶ。応答がなければプロセス・コンテナの状態を確認する。
2. 起動ログ(`Started RaiseTimeLineApplication`)と、直前の ERROR を見る。
3. DB(RDS)に接続できるか確認する。接続エラーなら DB の停止・接続上限・認証情報を疑う。
4. 直近のデプロイが原因なら、前のバージョンに戻す(ロールバック)。

### 8.2 A2 / A3 5xx・ERROR の増加
1. `http_request` の ERROR から、`http.route` を集計し、特定の API に偏っているか見る。
2. 該当の `request_id` で前後のログを追い、`error.stack_trace` を読む。
3. 原因が DB・S3 などの依存先なら、その状態を確認する。コードなら修正またはロールバック。
4. 収束したら、再発防止(テスト追加・アラート条件の見直し)を行う。

### 8.3 A4 遅延
1. 遅い `http.route` を特定する(`duration_ms` の上位)。
2. その API の SQL を見る(開発環境で `application-dev.properties` の DEBUG ログ。N+1 のガードは `TimelineSqlCountTest`)。
3. DB の負荷・接続プール・インデックスを確認する。

### 8.4 A5 / A7 ログイン失敗・429 の急増
1. `login_failed` の増加は、総当たり攻撃(クレデンシャルスタッフィング)の可能性がある。時間帯と件数を確認する。
2. ログには入力値を残していないため、IP など発信元はインフラ側(ロードバランサー/Nginx のアクセスログ、WAF)で確認する。
3. 継続するなら、発信元の遮断・レート制限の強化を検討する。

### 8.5 A6 トークン再利用の検知
1. `refresh_token_reuse_detected` の `user_id` を確認する。このユーザーの全リフレッシュトークンは、検知時点で自動的に失効済み(再ログインが必要)。
2. 複数タブの同時更新の猶予(`app.refresh.reuse-grace`、既定 10 秒)を超えた再提示なので、盗難または古い端末からの再利用の可能性がある。同一ユーザーで繰り返すなら、本人に連絡する。
3. 多数のユーザーで一斉に出る場合は、設定ミスやバグ(Cookie の扱い・時刻のずれ)を疑う。

## 9. 導入状況と今後

| 項目 | 状態 |
|---|---|
| 構造化ログ(JSON、`LOG_FORMAT`) | 実装済み |
| request_id・`X-Request-Id`・アクセスログ・user_id | 実装済み |
| 認証イベント(`login_*` / `user_registered` / `refresh_token_reuse_detected`) | 実装済み |
| 秘密情報がログに出ないことのテスト | 実装済み(`StructuredLoggingTest`) |
| ログの集約(CloudWatch Logs / Datadog)・保持期間 | 未実施(インフラ構築時。6 章) |
| アラート・ダッシュボード・外形監視 | 未実施(5 章の表を元に設定) |
| メトリクス・トレース | 未実施(7 章) |
| 本番の `LOG_FORMAT=ecs` の指定 | 未実施(デプロイ設定に追加) |

## 10. 見直し

- 月 1 回: うるさいアラートの削除、足りない観測の追加、ログの取り込み量・費用の確認。
- 障害のあと: 振り返り(原因・検知の遅れ・足りなかったログ)を行い、ログとアラートに反映する。責任追及ではなく、再発防止が目的。
- イベント名・フィールド名を変えるときは、監視の設定(メトリクスフィルタ・モニター)への影響を確認する。
