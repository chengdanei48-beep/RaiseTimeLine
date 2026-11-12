variable "aws_region" {
  description = "デプロイ先のリージョン(既定は東京)"
  type        = string
  default     = "ap-northeast-1"
}

variable "aws_profile" {
  description = "AWS CLI のプロファイル名(aws configure sso で作ったもの)"
  type        = string
  default     = "raisetimeline"
}

variable "project_name" {
  description = "リソース名の接頭辞"
  type        = string
  default     = "raisetimeline"
}

variable "budget_email" {
  description = "予算超過の通知先メールアドレス"
  type        = string
}

variable "budget_limit_usd" {
  description = "月の予算(USD)。超えそう・超えたときにメールで知らせる"
  type        = number
  default     = 1
}

variable "db_instance_class" {
  description = "RDS のインスタンスクラス。学習用なので最小クラス"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_storage_gb" {
  description = "RDS のディスク容量(GB)。gp3 の最小は 20"
  type        = number
  default     = 20
}

variable "db_name" {
  description = "RDS 内に作るデータベース名(開発用 docker-compose と同じ)"
  type        = string
  default     = "raisetimeline"
}

variable "db_username" {
  description = "RDS のマスターユーザー名(開発用 docker-compose と同じ)"
  type        = string
  default     = "raisetimeline"
}

variable "backend_image" {
  description = "バックエンド(Spring Boot)コンテナイメージのURI。CI/CDでECRにpushするまでの初期値はプレースホルダー(httpd)。ECSサービスは後からイメージを更新する運用"
  type        = string
  default     = "public.ecr.aws/docker/library/httpd:2.4"
}

variable "task_cpu" {
  description = "Fargateタスクに割り当てるCPU(vCPU×1024)。学習用の最小構成"
  type        = number
  default     = 256
}

variable "task_memory" {
  description = "Fargateタスクに割り当てるメモリ(MiB)"
  type        = number
  default     = 512
}

variable "desired_count" {
  description = "ECSサービスの起動タスク数。使わない間は0にしてコストを止められる"
  type        = number
  default     = 1
}

variable "cloudfront_price_class" {
  description = "CloudFrontの配信クラス。学習用は北米・欧州・アジア中心のPriceClass_200で十分"
  type        = string
  default     = "PriceClass_200"
}
