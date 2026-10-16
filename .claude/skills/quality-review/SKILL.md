---
name: quality-review
description: このリポジトリ(Spring Boot + React + Terraform/AWS)の全体品質レビュー。標準的な実装からの逸脱と、要件定義書・設計書(docs/)と実装の差異を点検し、静的解析・テストを実行する。Terraform(infra/terraform/)と運用スクリプト(scripts/*.ps1)も対象。「品質チェック」「レビューして」「docsと実装の差異」「PR前の確認」「Terraformのチェック」などで使う。
---

# 品質レビュー(quality-review)

機能追加・修正の PR を出す前や、定期的な全体点検で使うチェックリスト。
**ドキュメント(`docs/`)を正として**実装を直す。ただし docs に無い実在機能は、docs 側に追記する。

## 手順

1. 下記「自動チェック」を実行し、全て通す。
2. 「観点チェックリスト」を BE / FE / インフラ(`infra/terraform/`・`scripts/`)のうち、変更した範囲について点検し、指摘を `ファイル:行` つきでまとめる。インフラを変更した PR では、インフラの項目を必ず実施する。
3. 「docs ⇔ 実装の差異」を確認する。差異は docs を正として実装を直す(docs に無い実在機能は docs へ追記)。
4. 修正は Issue → 専用ブランチ → PR(CLAUDE.md の開発フロー)で行う。動作確認のサーバー起動は CLAUDE.md のポート規約に従う。

## 自動チェック

```
cd backend  && ./mvnw verify          # テスト + Spotless(整形) + Checkstyle + SpotBugs
cd backend  && ./mvnw spotless:apply  # 整形の自動修正
cd frontend && npm run lint           # Oxlint
cd frontend && npm run typecheck      # tsc -b
cd frontend && npm run build
```

- テストは PostgreSQL(既定 5433)が起動している前提。Windows では `JAVA_HOME`(JDK 25)が必要。
- CI(`.github/workflows/ci.yml`)でも同じものを実行する。ツールの設定は `backend/checkstyle.xml`、`backend/spotbugs-exclude.xml`、`frontend/.oxlintrc.json`。
- 静的解析の除外は誤検知に限り、理由をコメントで残す。

### Terraform / スクリプト(`infra/terraform/` を変更したとき)

```
cd infra/terraform && terraform fmt -check -recursive   # 整形(直すときは terraform fmt)
cd infra/terraform && terraform init -input=false -backend=false && terraform validate
cd infra/terraform && terraform plan -input=false -out tfplan   # 要 aws sso login。読み取りのみ(作成はしない)
```

- `plan` の末尾 `Plan: N to add, M to change, K to destroy.` と、作られる資源の種類を読む(下の「費用」の観点)。確認後は `tfplan` を削除する。**`apply` / `destroy` は、ユーザーの承認なしに実行しない**(`docs/aws-deploy-guide.md` 8章)。
- 静的解析: `cd infra/terraform && tflint --init && tflint`(設定は `.tflint.hcl`)、`trivy config infra/terraform --severity MEDIUM,HIGH,CRITICAL`。未導入なら、その旨を報告し、下の観点を目視で点検する(ツールはシステムに入れず、リリースの zip を一時フォルダに展開すれば使える)。
- 指摘は、まず直す。意図した設定は、`.tf` に `#trivy:ignore:AWS-xxxx` と **理由のコメント** を書いて除外する(誤検知・費用・運用方針による)。理由なしの除外は不可。
- PowerShell スクリプト: `Invoke-ScriptAnalyzer -Path scripts -Settings scripts/PSScriptAnalyzerSettings.psd1`(Windows PowerShell 5.1 向け。`Write-Host` と `Confirm-Yes` の誤検知は設定で除外済み)。
- CI(`.github/workflows/ci.yml`)にも同じものがある: `terraform` ジョブ(`fmt -check` / `init -backend=false` / `validate` / TFLint / Trivy)と `powershell` ジョブ(PSScriptAnalyzer)。AWS の認証を使わないため、`plan` は含まない(`plan` はローカルで実行して確認する)。

## 観点チェックリスト

### バックエンド(Spring Boot / JPA / Security)

- 入力検証: DTO に Bean Validation(`@NotBlank` `@Size` 等)があり、Controller の `@RequestBody` に `@Valid` がある。手書きの検証を Service に重ねていない。
- エラー応答: `@RestControllerAdvice`(ProblemDetail)で統一。404 は `ResourceNotFoundException`。Service が `boolean` / `Optional` で「見つからない」を返していない。
- HTTP: 作成は 201 + `Location`。ステータスの使い分け(400/401/403/404/409)が一貫している。Controller はクラス単位の `@RequestMapping`。
- トランザクション: Service は `@Transactional(readOnly = true)` を基本とし、更新メソッドだけ `@Transactional`。`spring.jpa.open-in-view=false` で、遅延ロードは Service 内で完結。
- JPA: N+1 がない(集計は GROUP BY、関連は EntityGraph / fetch join)。LIKE のワイルドカードをエスケープ。エンティティを API で直接返さない(DTO を使う)。
- 競合・制約: 一意制約違反が 500 にならず 409 になる。上限チェック(列10個など)の競合を意識している。
- セキュリティ: 公開パスが最小(`/api/auth/register`・`login`・`health`、静的ファイル)。actuator は health のみ。セッション Cookie(http-only / same-site)、CSRF、パスワードをログ・`toString` に出さない。
- 設定: DB パスワード等は環境変数で上書き可能。`show-sql` など開発用設定は `dev` プロファイルのみ。
- テスト: 正常系だけでなく、境界値(文字数上限)・他人のデータ(404)・未ログイン(401)・CSRF(403)を確認している。

### フロントエンド(React / TypeScript / Vite)

- `npm run lint` / `typecheck` / `build` が警告なしで通る。`tsconfig` は `strict`。
- Hooks: `useEffect` の依存配列が正しい。親の再描画のたびに listener を付け替えない(`useCallback` / ref)。
- エラー処理: ユーザーに URL・ステータスコード等の内部情報を見せない。401 はログイン画面へ戻す(共通処理)。通信失敗を「未ログイン」と混同しない。
- アクセシビリティ: モーダルはネイティブ `<dialog>`(フォーカストラップ・Esc)。入力エラーに `role="alert"` と `aria-invalid` / `aria-describedby`。色だけで情報を伝えない。
- CSRF: 更新系リクエストに `X-XSRF-TOKEN` を付けている。
- 型: `any` / 不要な `!` を使わない。API の型は `types.ts` に集約。

### インフラ(Terraform / AWS / 運用スクリプト)

**セキュリティ**
- ネットワーク: セキュリティグループの受信は必要最小限(80/443 のみ。22 を開けない)。DB の 5432 は **アプリの SG からのみ**(CIDR で開けない)。RDS は `publicly_accessible = false`。`0.0.0.0/0` の受信は意図して許可したものだけ。
- IAM: ロールの権限は最小(ARN を絞る。`*` のアクションやリソースを避ける)。サービスに付けるのは人の権限ではなくロール。アクセスキーを作らない・コードに書かない。
- 暗号化: EBS(`encrypted = true`)、RDS(`storage_encrypted = true`)、S3(SSE)、SecureString。S3 は `public_access_block` の4項目が `true`。EC2 は IMDSv2(`http_tokens = "required"`)。DB 接続は SSL(`sslmode=require`)。
- 秘密情報: パスワード・トークンを `.tf`・テンプレート・ドキュメント・ログに書かない(`random_password` + SSM)。出力(`output`)にパスワードを出さない(必要なら `sensitive = true`)。スクリプトは秘密情報を画面に出さない。
- state: `*.tfstate`・`*.tfvars`・`tfplan` が `.gitignore` にある(`git ls-files infra` に混ざっていない)。state には生成したパスワードが入ることをドキュメントに書く。`.terraform.lock.hcl` はコミットする。

**費用・課金事故**
- `plan` に **NAT Gateway・ALB/NLB・Multi-AZ・大きなインスタンス** が含まれていない(8章のルール。入れるときは提案して承認を得る)。
- RDS: クラスは最小、`multi_az = false`、`skip_final_snapshot` と `backup_retention_period` は方針(`docs` 6章)どおり。スナップショットを残す設定なら、削除手順に書く。
- 「使うときだけ作る」運用: `destroy` で課金対象が全て消える(`force_destroy`、`deletion_protection = false`)。`aws-down.ps1` の消し残し確認が、追加した資源の種類を網羅している。
- 全資源にタグ(`default_tags` の `Project`)が付く。タグが付かない資源(RDS のサブネットグループ等)は、名前で確認できる。
- 予算アラート(`budget.tf`)が有効で、通知先が設定されている。

**信頼性・保守性**
- バージョン固定: `required_version` と provider の `version`(`~>`)。`.terraform.lock.hcl` が最新の provider(`random` など)を含む。
- 依存関係: 暗黙の依存で足りない順序は `depends_on` で明示(例: ロールのポリシー付与後に EC2 を起動)。`lifecycle.ignore_changes` は理由をコメントに書く。
- 変数: `description` と `type` がある。環境ごとに変えるものだけ変数にし、既定値は安全側(最小・非公開)。`terraform.tfvars.example` が実際の変数と一致している。
- `templatefile` / `user_data`: 展開結果を確認する(`terraform console` にダミー値を渡す)。bash の `${...}` は Terraform に解釈されるので注意。失敗しうる処理(IAM 反映待ち、ネットワーク)にリトライがある。`set -euo pipefail`。
- 重複・未使用: 使われていない変数・出力・data がない。同じ値のハードコードがない(リージョン等は変数から)。

**スクリプト(`scripts/*.ps1`)**
- Windows PowerShell 5.1 で動く: 日本語を含むファイルは **UTF-8 BOM 付き**。`&&` `?.` `??` を使わない。`$ErrorActionPreference = 'Stop'` のまま native コマンドの stderr を捨てる(`2>$null`)と例外になる。
- ネイティブコマンドの終了コード(`$LASTEXITCODE`)を確認している。`aws ssm wait` は約100秒で打ち切られる(長い処理は自前でポーリング)。
- 破壊的な操作(`destroy`)は、確認(`yes`)を挟む。何を消すかを先に表示する。
- 一時ファイル(`taskmgmt-*-params.json` など)を `finally` で消す。AWS CLI へ渡す JSON は ASCII のみ(日本語は `\uXXXX`)。
- 実行した結果(出力・消し残し)をユーザーに分かる形で表示する。

**ポート規約**: 動作確認のためにローカルでサーバーを起動するときは、CLAUDE.md のポート規約(8080 / 5173 / 5433、別ポートへ逃げない)に従う。

### docs ⇔ 実装の差異(docs を正とする)

`docs/requirements.md`・`use-cases.md`・`diagrams.md`・`screen-design.html` と実装を突き合わせる。

- 画面の文言(ボタン・見出し・ヒント・必須表示)、表示形式(日付 `YYYY/MM/DD`、優先度は「重・中・低」)。
- 入力制限(ユーザー名1〜50、パスワード8〜72、列名1〜20、列数10、タイトル1〜50、説明500、ラベル名1〜20)と、エラー表示の仕様。
- 画面項目・操作(確認用パスワード、削除確認ダイアログ、作成日時の表示 等)。
- API(パス・フィールド・ステータスコード・クエリパラメータ)と DB 定義(型・長さ・制約・値の大文字小文字)。
- docs 同士の整合(参照している要件定義書のバージョン、ER 図とテーブル定義)。
- 新機能を入れたら docs(要件・ユースケース・ER図・画面設計・README)を同じ PR で更新する。

**インフラの docs(`docs/aws-deploy-guide.md`)** と、`infra/terraform/`・`scripts/` を突き合わせる。

- 構成図・部品の表・ファイル構成の表が、実際の `.tf` / `templates/` と一致している(増減したファイルが載っている)。
- 段階の表(状態)、費用の目安、環境変数の表、手順(コマンド・スクリプト名・引数)が実装と一致している。
- トラブルシュートに、実際に起きた失敗(原因と対処)を追記している。
- 設計方針(RDS のスナップショット等)が、コードとガイドで食い違っていない。
- `README.md`・`docs/diagrams.md` に AWS 構成の記述があれば、同じ内容に更新する。
