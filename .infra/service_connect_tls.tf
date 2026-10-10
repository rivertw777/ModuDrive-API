# Service Connect TLS (prod: hardened). The Envoy sidecars encrypt service-to-service traffic with
# short-lived certificates from this private CA, rotated by ECS; the apps still speak plain HTTP to
# their local proxy, so no app change.
resource "aws_acmpca_certificate_authority" "service_connect" {
  count = var.hardened ? 1 : 0

  type       = "ROOT"
  usage_mode = "SHORT_LIVED_CERTIFICATE"

  certificate_authority_configuration {
    key_algorithm     = "EC_prime256v1"
    signing_algorithm = "SHA256WITHECDSA"
    subject {
      common_name = "${var.project}-service-connect"
    }
  }

  permanent_deletion_time_in_days = 30

  # The managed ECS infrastructure policy only lets ECS use CAs carrying this tag.
  tags = {
    AmazonECSManaged = "true"
  }
}

resource "aws_acmpca_certificate" "service_connect" {
  count = var.hardened ? 1 : 0

  certificate_authority_arn   = aws_acmpca_certificate_authority.service_connect[0].arn
  certificate_signing_request = aws_acmpca_certificate_authority.service_connect[0].certificate_signing_request
  signing_algorithm           = "SHA256WITHECDSA"
  template_arn                = "arn:aws:acm-pca:::template/RootCACertificate/V1"

  validity {
    type  = "YEARS"
    value = 10
  }
}

# Installing its own certificate is what makes the CA ACTIVE.
resource "aws_acmpca_certificate_authority_certificate" "service_connect" {
  count = var.hardened ? 1 : 0

  certificate_authority_arn = aws_acmpca_certificate_authority.service_connect[0].arn
  certificate               = aws_acmpca_certificate.service_connect[0].certificate
  certificate_chain         = aws_acmpca_certificate.service_connect[0].certificate_chain
}

# ECS itself (not the tasks) issues the certificates and keeps their keys in Secrets Manager.
data "aws_iam_policy_document" "ecs_infrastructure_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_infrastructure" {
  count = var.hardened ? 1 : 0

  name               = "${var.project}-ecs-infrastructure"
  assume_role_policy = data.aws_iam_policy_document.ecs_infrastructure_assume.json
}

resource "aws_iam_role_policy_attachment" "ecs_infrastructure" {
  count = var.hardened ? 1 : 0

  role       = aws_iam_role.ecs_infrastructure[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSInfrastructureRolePolicyForServiceConnectTransportLayerSecurity"
}

# The certificates' private keys sit in Secrets Manager under the data key, like every other secret.
data "aws_iam_policy_document" "ecs_infrastructure_kms" {
  count = var.hardened ? 1 : 0

  statement {
    actions   = ["kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey*", "kms:DescribeKey"]
    resources = [local.kms_key_arn]
  }
}

resource "aws_iam_role_policy" "ecs_infrastructure_kms" {
  count = var.hardened ? 1 : 0

  name   = "kms"
  role   = aws_iam_role.ecs_infrastructure[0].id
  policy = data.aws_iam_policy_document.ecs_infrastructure_kms[0].json
}
