output "cloudfront_domain_name" {
  description = "アプリのURL(フロントエンド・API共通のドメイン)"
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "alb_dns_name" {
  description = "ALBのDNS名(CloudFrontのオリジン。直接アクセスは想定しない)"
  value       = aws_lb.main.dns_name
}

output "frontend_bucket" {
  description = "フロントエンドのビルド出力を置くS3バケット名(CI/CDがここにsyncする)"
  value       = aws_s3_bucket.frontend.bucket
}

output "images_bucket" {
  description = "画像用S3バケット名"
  value       = aws_s3_bucket.images.bucket
}

output "ecr_repository_url" {
  description = "バックエンドのコンテナイメージをpushするECRリポジトリURL"
  value       = aws_ecr_repository.backend.repository_url
}

output "ecs_cluster_name" {
  description = "ECSクラスタ名"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECSサービス名(イメージ更新後の再デプロイに使う)"
  value       = aws_ecs_service.backend.name
}

output "db_endpoint" {
  description = "RDS の接続先ホスト名(VPC の中からだけ届く)"
  value       = aws_db_instance.main.address
}

output "db_password_parameter" {
  description = "DB パスワードを保管した SSM パラメータ名"
  value       = aws_ssm_parameter.db_password.name
}

output "jwt_secret_parameter" {
  description = "JWT署名鍵を保管したSSMパラメータ名"
  value       = aws_ssm_parameter.jwt_secret.name
}

output "cloudfront_distribution_id" {
  description = "CloudFrontのディストリビューションID(デプロイ後のキャッシュ無効化に使う)"
  value       = aws_cloudfront_distribution.main.id
}

output "ecs_task_family" {
  description = "ECSタスク定義のfamily名(デプロイ時に新リビジョンを登録する対象)"
  value       = aws_ecs_task_definition.backend.family
}

output "deploy_role_arn" {
  description = "GitHub ActionsがAssumeするデプロイ用IAMロールのARN(.github/workflows/deploy.yml 用)"
  value       = aws_iam_role.deploy.arn
}
