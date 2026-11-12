# CloudFront: 1ディストリビューションでフロント(S3)とAPI(ALB)を1ドメインに統合する。
# デフォルトビヘイビア→S3(静的ファイル)、パスパターン /api/* →ALB(ECS Fargate)。
# 独自ドメインが未定のため、証明書はCloudFront既定のもの(*.cloudfront.net)を使う。
# 独自ドメインが決まったら、us-east-1のACM証明書を発行してaliasesとviewer_certificateに追加する(docs/05-infrastructure.md 7章)。

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

# 除外の理由:
# - AWS-0011(WAF): 学習用で、WAFの固定費(月$5〜)を避ける。公開範囲を広げる場合に検討(docs/05-infrastructure.md 7章)
# - AWS-0010(アクセスログ): 学習用で、ログ保管用S3バケットの追加コストを避ける
#trivy:ignore:AWS-0011
#trivy:ignore:AWS-0010
resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  default_root_object = "index.html"
  price_class         = var.cloudfront_price_class
  comment             = "${var.project_name} frontend + API"

  origin {
    origin_id                = "frontend-s3"
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  origin {
    origin_id   = "backend-alb"
    domain_name = aws_lb.main.dns_name

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only" # ALBはCloudFrontからの通信のみ受けるため、AWS内部はHTTPのまま(独自ドメイン導入時にHTTPS化を検討)
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = "frontend-s3"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
  }

  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "backend-alb"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
  }

  # React Router(SPA)のパスを直接開いたとき、S3が返す403/404をindex.htmlにフォールバックさせる
  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }

  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = {
    Name = "${var.project_name}-cdn"
  }
}
