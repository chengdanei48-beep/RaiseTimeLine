# バックエンド(Spring Boot)のコンテナイメージを置くリポジトリ。CI/CD(別issue)がビルドしてpushする想定。
# タグは IMMUTABLE(上書き不可)。pushするタグは "latest" ではなく、コミットSHAなど一意の値にする。
resource "aws_ecr_repository" "backend" {
  name                 = "${var.project_name}-backend"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = true # terraform destroy のときに中身ごと消す(学習用)

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${var.project_name}-backend"
  }
}

# 古いイメージが無限に溜まらないよう、直近10件だけ残す
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "直近10件のイメージだけ残す"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
