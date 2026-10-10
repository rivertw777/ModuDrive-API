# One customer-managed key for data at rest (hardened, i.e. prod): Aurora, MemoryDB, the S3 blocks,
# SQS, the SSM secrets, CloudWatch Logs, CloudTrail and the RDS-managed admin passwords. Unlike the AWS-managed
# keys, its policy and every use are ours to control and audit (CloudTrail), and it rotates yearly.
# Without hardened everything stays on the AWS-managed/service-owned encryption it had.
resource "aws_kms_key" "data" {
  count = var.hardened ? 1 : 0

  description             = "${var.project} data at rest: databases, Redis, S3 blocks, queues, secrets, logs"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  policy                  = data.aws_iam_policy_document.kms_backup_account.json
}

resource "aws_kms_alias" "data" {
  count = var.hardened ? 1 : 0

  name          = "alias/${var.project}-data"
  target_key_id = aws_kms_key.data[0].key_id
}

data "aws_iam_policy_document" "kms_data" {
  # The account manages the key; who may use it is then up to IAM policies (iam.tf).
  statement {
    actions   = ["kms:*"]
    resources = ["*"]
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }

  # CloudWatch Logs encrypts this stack's log groups with it.
  statement {
    actions   = ["kms:Encrypt*", "kms:Decrypt*", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:Describe*"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["logs.${var.region}.amazonaws.com"]
    }
    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:*"]
    }
  }

  # CloudTrail encrypts the trail's log files (audit.tf); reading them back is IAM's call.
  statement {
    actions   = ["kms:GenerateDataKey*"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [local.trail_arn]
    }
    condition {
      test     = "StringLike"
      variable = "kms:EncryptionContext:aws:cloudtrail:arn"
      values   = ["arn:aws:cloudtrail:*:${local.account_id}:trail/*"]
    }
  }

  statement {
    actions   = ["kms:DescribeKey"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
  }

  # SES's Send events reach mail-ses-events through SNS, which has to encrypt into the queue.
  statement {
    actions   = ["kms:GenerateDataKey*", "kms:Decrypt"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.mail_ses_events.arn]
    }
  }
}

# An Aurora snapshot under this key can be copied to the backup account (backup.tf) only if that
# account may use the key to re-encrypt it under its own.
data "aws_iam_policy_document" "kms_backup_account" {
  source_policy_documents = [data.aws_iam_policy_document.kms_data.json]

  dynamic "statement" {
    for_each = local.backup_copy ? [1] : []
    content {
      sid       = "BackupAccountCopy"
      actions   = ["kms:Decrypt", "kms:DescribeKey", "kms:CreateGrant", "kms:ReEncrypt*", "kms:GenerateDataKey*"]
      resources = ["*"]
      principals {
        type        = "AWS"
        identifiers = ["arn:aws:iam::${local.backup_account_id}:root"]
      }
    }
  }
}

locals {
  # null without hardened: each resource falls back to its default encryption.
  kms_key_arn = var.hardened ? aws_kms_key.data[0].arn : null
}
