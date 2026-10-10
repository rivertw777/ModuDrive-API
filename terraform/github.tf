# The role the deploy workflow (.github/workflows/deploy.yml) assumes through GitHub's OIDC — no
# long-lived keys anywhere. It can push images and roll the services to a new revision, nothing else:
# infrastructure changes stay with whoever runs terraform apply.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

# Only jobs that run in this environment's GitHub environment — protection rules there (reviewers,
# branches) are what gate a prod deploy.
data "aws_iam_policy_document" "github_deploy_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:${var.environment}"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "${var.project}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_deploy_assume.json
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }
  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage", "ecr:DescribeImages",
    ]
    resources = [for repo in aws_ecr_repository.service : repo.arn]
  }
  # Task definitions have no resource-level scoping for these two.
  statement {
    actions   = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"]
    resources = ["*"]
  }
  statement {
    actions   = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = [for service in aws_ecs_service.service : service.id]
  }
  # Registering a revision hands it the same roles Terraform gave it.
  statement {
    actions   = ["iam:PassRole"]
    resources = concat([aws_iam_role.execution.arn], [for role in aws_iam_role.task : role.arn])
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}

# The role the deploy workflow's terraform job assumes (demo: a merge into the demo branch applies
# terraform/). Terraform manages IAM, KMS and the network, so this one can do anything in the account —
# which is why it exists only where var.terraform_in_ci says so, and trusts only the <env>-terraform
# GitHub environment (limit that environment to its branch in GitHub).
data "aws_iam_policy_document" "github_terraform_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:${var.environment}-terraform"]
    }
  }
}

resource "aws_iam_role" "github_terraform" {
  count = var.terraform_in_ci ? 1 : 0

  name                 = "${var.project}-github-terraform"
  assume_role_policy   = data.aws_iam_policy_document.github_terraform_assume.json
  max_session_duration = 3600
}

resource "aws_iam_role_policy_attachment" "github_terraform" {
  count = var.terraform_in_ci ? 1 : 0

  role       = aws_iam_role.github_terraform[0].name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}
