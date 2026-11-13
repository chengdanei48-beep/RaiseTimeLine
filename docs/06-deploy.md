# 06. デプロイ(CI/CD)

[05-infrastructure.md](05-infrastructure.md) の構成に対して、GitHub Actions(`.github/workflows/deploy.yml`)で自動デプロイする手順。

## 1. 全体の流れ

```
①初回のみ terraform apply(インフラの箱を作成)
②GitHub Variablesにterraform outputの値を設定
③main へ push(backend/** または frontend/** の変更)→ deploy.yml が自動実行
```

①②は手動(1回だけ)、③は以降のデプロイで自動的に行われる。

## 2. 初回セットアップ

### 2.1 terraform apply

```sh
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars  # budget_email を自分のメールアドレスに書き換える
terraform init
terraform apply
```

初回applyの直後は、ECSサービスのコンテナイメージがプレースホルダー(`var.backend_image` の既定値)のままなので、ALBのヘルスチェックが失敗し続ける(タスクが再起動を繰り返す)。これは次の 2.3(初回デプロイ)で解消する。

### 2.2 GitHub Variables の設定

リポジトリの **Settings > Secrets and variables > Actions > Variables** に、`terraform output` の値を設定する(すべて非シークレットな識別子。OIDC連携のためAWSアクセスキーは不要)。

| Variable名 | 値(terraform output) |
|---|---|
| `AWS_REGION` | `ap-northeast-1` |
| `AWS_DEPLOY_ROLE_ARN` | `deploy_role_arn` |
| `ECR_REPOSITORY` | `${var.project_name}-backend`(例: `raisetimeline-backend`) |
| `ECS_CLUSTER` | `ecs_cluster_name` |
| `ECS_SERVICE` | `ecs_service_name` |
| `ECS_TASK_FAMILY` | `ecs_task_family` |
| `FRONTEND_BUCKET` | `frontend_bucket` |
| `CLOUDFRONT_DISTRIBUTION_ID` | `cloudfront_distribution_id` |

```sh
terraform output -raw deploy_role_arn
# ... 他のoutputも同様に確認し、gh variable set で設定する
gh variable set AWS_DEPLOY_ROLE_ARN --body "$(terraform output -raw deploy_role_arn)"
```

### 2.3 初回デプロイ

main へ push する(または `gh workflow run deploy.yml`)と、`deploy.yml` が実行され、実際のアプリのコンテナイメージがECRへpushされ、ECSサービスが更新される。フロントエンドも同時にS3へ同期され、CloudFrontのキャッシュが無効化される。

初回はCloudFrontの反映に15〜20分程度かかることがある。

## 3. 動作確認

```sh
terraform output -raw cloudfront_domain_name
```

で得られるURLにアクセスし、ページが表示されること、`<URL>/api/health` が 200 を返すことを確認する。

## 4. ECS Exec(コンテナにシェルで入る)

SSH の代わり。事前に [Session Manager plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) をローカルにインストールしておく。

```sh
aws ecs execute-command \
  --cluster $(terraform output -raw ecs_cluster_name) \
  --task <タスクID> \
  --container backend \
  --interactive \
  --command "/bin/sh"
```

## 5. 止め方(コストを抑える)

- 一時的に止める: ECSサービスのタスク数を0にする(`desired_count = 0` にして `terraform apply`、または `aws ecs update-service --desired-count 0`)。ALB・RDS・S3・CloudFrontは残るため、課金は完全には止まらない。
- 完全に消す: `terraform destroy`(RDSはスナップショットを残さない設定のため、データは失われる)。

## 6. スコープ外

- 独自ドメイン・ACM証明書の追加(docs/05-infrastructure.md 7章)
- デプロイ前のマイグレーション確認・ロールバック手順の自動化
