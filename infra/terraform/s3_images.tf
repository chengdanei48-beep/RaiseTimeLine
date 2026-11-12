# 画像用バケット(docs/05-infrastructure.md 4章)。posts/{userId}/{uuid}.{ext} と avatars/{userId}/{uuid}.{ext} を置く。
# 署名付きURLのみでアクセスする非公開バケット。ブラウザがCloudFront経由で取得したURLでS3へ直接PUT/GETする。
data "aws_caller_identity" "current" {}

# 除外の理由: AWS-0090(バージョニング)。学習用で、保管コストを避ける。残したい画像が出たら有効化を検討。
#trivy:ignore:AWS-0090
resource "aws_s3_bucket" "images" {
  bucket = "${var.project_name}-images-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "images" {
  bucket                  = aws_s3_bucket.images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 除外の理由: AWS-0132(顧客管理キー)はキーの月額課金が発生する。学習用なのでAWS管理キー(SSE-S3)で足りる。
#trivy:ignore:AWS-0132
resource "aws_s3_bucket_server_side_encryption_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# ブラウザがCloudFrontのドメインから署名付きURLでS3へ直接PUT/GETするためのCORS設定
resource "aws_s3_bucket_cors_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  cors_rule {
    allowed_methods = ["PUT", "GET", "HEAD"]
    allowed_origins = ["https://${aws_cloudfront_distribution.main.domain_name}"]
    allowed_headers = ["Content-Type"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

# HTTPS 以外のアクセスを拒否する
data "aws_iam_policy_document" "images_tls_only" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.images.arn, "${aws_s3_bucket.images.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "images" {
  bucket = aws_s3_bucket.images.id
  policy = data.aws_iam_policy_document.images_tls_only.json

  # 公開ブロックの設定と同時に更新すると競合することがあるため、先に公開ブロックを済ませる
  depends_on = [aws_s3_bucket_public_access_block.images]
}
