# DB パスワードと JWT 署名鍵は Terraform が自動生成し、SSM Parameter Store に暗号化して保管する。
# ECS タスクが起動時に取得する(コンテナ定義の secrets)。コードや Git には書かない。
# 注意: 生成した値は terraform.tfstate にも入る。tfstate は Git に入れない・他人に渡さない。
resource "random_password" "db" {
  length  = 32
  special = false # 環境変数や URL で扱いやすい文字だけにする
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project_name}/db_password"
  type  = "SecureString"
  value = random_password.db.result
}

resource "random_password" "jwt_secret" {
  length  = 44 # 32バイト以上(base64相当の文字数で余裕を持たせる)
  special = false
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${var.project_name}/jwt_secret"
  type  = "SecureString"
  value = random_password.jwt_secret.result
}
