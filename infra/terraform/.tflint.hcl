# tflint の設定。ローカル: cd infra/terraform && tflint --init && tflint

# Terraform 標準のルール(未使用の変数・data、型の指定、命名など)
plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

# AWS 固有のルール(存在しないインスタンスタイプ・無効な値など)
plugin "aws" {
  enabled = true
  version = "0.49.0"
  source  = "github.com/terraform-linters/tflint-ruleset-aws"
}
