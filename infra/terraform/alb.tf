# ALB: CloudFrontから受けて、ECS Fargateタスクへ振り分ける。
# 除外の理由: AWS-0053(パブリック公開)。CloudFrontがオリジンとして到達する必要があるため、ALBはインターネットからの到達性を持つ。
# 受信自体はCloudFrontのプレフィックスリストに絞っている(network.tfのalb_sg)。
#trivy:ignore:AWS-0053
resource "aws_lb" "main" {
  name                       = "${var.project_name}-alb"
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = data.aws_subnets.default.ids
  drop_invalid_header_fields = true

  tags = {
    Name = "${var.project_name}-alb"
  }
}

resource "aws_lb_target_group" "app" {
  name        = "${var.project_name}-app"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = data.aws_vpc.default.id
  target_type = "ip"

  health_check {
    path                = "/api/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
    timeout             = 5
    matcher             = "200"
  }

  tags = {
    Name = "${var.project_name}-app"
  }
}

# HTTPSはCloudFrontのエッジで終端する(独自ドメイン・ACM証明書が未定のため)。CloudFront-ALB間はHTTPのみで、AWSのネットワーク内を通る。
# 除外の理由: AWS-0054(HTTPSリスナー必須)。独自ドメインが決まったら ap-northeast-1 のACM証明書を追加し、HTTPSリスナーに切り替える(docs/05-infrastructure.md 7章の未確定事項)。
#trivy:ignore:AWS-0054
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}
