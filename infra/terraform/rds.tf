# RDS(PostgreSQL)。毎回作り直す運用で、データは残さない方針(docs/05-infrastructure.md 7章)。

# RDS は2つ以上の AZ のサブネットが必須。デフォルトVPCには各AZにサブネットがあるので、それを束ねる。
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db"
  subnet_ids = data.aws_subnets.default.ids

  tags = {
    Name = "${var.project_name}-db"
  }
}

# DB には Fargateタスクの SG からの 5432 だけ許可する。インターネットからは届かない。
resource "aws_security_group" "db" {
  name        = "${var.project_name}-db"
  description = "Allow PostgreSQL from the Fargate tasks only"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "${var.project_name}-db"
  }
}

resource "aws_vpc_security_group_ingress_rule" "db_from_fargate" {
  security_group_id            = aws_security_group.db.id
  description                  = "PostgreSQL from Fargate tasks"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.fargate.id
}

# 除外の理由(すべて「使うときだけ作り、終わったら消す」学習用の運用と、アプリの方式による):
# - AWS-0077: 自動バックアップなし。データを残さない方針
# - AWS-0177: 削除保護なし。terraform destroy で消せるようにする
# - AWS-0176: IAM 認証なし。アプリ(Spring Boot)はパスワード認証で、パスワードは SSM に暗号化して保管している
#trivy:ignore:AWS-0077
#trivy:ignore:AWS-0177
#trivy:ignore:AWS-0176
resource "aws_db_instance" "main" {
  identifier = "${var.project_name}-db"

  engine         = "postgres"
  engine_version = "17" # docker-compose.yml(開発用)の postgres:17 に合わせる
  instance_class = var.db_instance_class

  allocated_storage = var.db_storage_gb
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  # 毎回作り直す運用で、データは残さない
  skip_final_snapshot     = true
  backup_retention_period = 0
  deletion_protection     = false

  apply_immediately = true

  tags = {
    Name = "${var.project_name}-db"
  }
}
