# AWS が最初から用意している「デフォルトVPC」を使う(NAT Gateway など課金されるものを作らない)
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }

  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

# CloudFrontからの通信だけを許可するための、AWS管理のプレフィックスリスト(CloudFrontのオリジン側IPレンジ)
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# ALB: CloudFrontからの80番だけ許可する(利用者が直接ALBを叩けないようにする)
resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb"
  description = "Allow HTTP from CloudFront only"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "${var.project_name}-alb"
  }
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from CloudFront"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
}

resource "aws_vpc_security_group_egress_rule" "alb_to_fargate" {
  security_group_id            = aws_security_group.alb.id
  description                  = "To Fargate tasks"
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.fargate.id
}

# Fargateタスク: ALBからの8080だけ受信。送信はNAT Gatewayを使わず、パブリックIPで外(ECR・RDS・S3・CloudWatch)へ出る。
resource "aws_security_group" "fargate" {
  name        = "${var.project_name}-fargate"
  description = "Allow HTTP from ALB only"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "${var.project_name}-fargate"
  }
}

resource "aws_vpc_security_group_ingress_rule" "fargate_from_alb" {
  security_group_id            = aws_security_group.fargate.id
  description                  = "HTTP from ALB"
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.alb.id
}

# 除外の理由: AWS-0104。NAT Gateway・VPCエンドポイントを使わない構成(課金を避ける)のため、宛先(ECR・RDS・S3・CloudWatch Logs)を絞れない。
# 受信は fargate_from_alb と db 側のSGで絞っている。
#trivy:ignore:AWS-0104
resource "aws_vpc_security_group_egress_rule" "fargate_all" {
  security_group_id = aws_security_group.fargate.id
  description       = "All outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
