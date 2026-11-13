data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# タスク実行ロール: ECSエージェントがコンテナイメージのpull・ログ送信・secrets取得に使う(アプリ自身は使わない)
resource "aws_iam_role" "execution" {
  name               = "${var.project_name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# コンテナ定義の secrets(DBパスワード・JWT鍵)をSSM Parameter Storeから取得する権限
data "aws_iam_policy_document" "execution_secrets" {
  statement {
    sid     = "ReadSecrets"
    actions = ["ssm:GetParameters"]
    resources = [
      aws_ssm_parameter.db_password.arn,
      aws_ssm_parameter.jwt_secret.arn,
    ]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "${var.project_name}-ecs-execution-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

# タスクロール: アプリ(Spring Boot)自身が使う権限(画像用S3へのアクセス、ECS Execの通信)
resource "aws_iam_role" "task" {
  name               = "${var.project_name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "task_access" {
  statement {
    sid       = "ImagesBucketAccess"
    actions   = ["s3:PutObject", "s3:GetObject", "s3:HeadObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.images.arn}/*"]
  }
}

resource "aws_iam_role_policy" "task_access" {
  name   = "${var.project_name}-ecs-task-access"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_access.json
}

# ECS Exec(aws ecs execute-command)でコンテナにシェルで入れるようにする(SSHの代わり)。必要最小限の権限のみ付ける。
data "aws_iam_policy_document" "task_ssmmessages" {
  statement {
    sid = "EcsExec"
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "task_ssmmessages" {
  name   = "${var.project_name}-ecs-task-exec"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_ssmmessages.json
}
