# Audit and threat detection (prod: hardened). Who did what to the account (CloudTrail), what moved on
# the network (VPC Flow Logs), what looks hostile (GuardDuty) and how far the setup drifts from AWS's
# baseline (Security Hub on top of Config). The logs land in one bucket, kept a year.
locals {
  account_id = data.aws_caller_identity.current.account_id
  # Built rather than read from the trail: the KMS key policy needs it before the trail exists.
  trail_arn = "arn:aws:cloudtrail:${var.region}:${local.account_id}:trail/${var.project}"
}

resource "aws_s3_bucket" "logs" {
  count = var.hardened ? 1 : 0

  bucket = "${var.project}-logs-${local.account_id}"
  # Write once: not even an intruder holding s3:DeleteObject can erase their tracks in the first year.
  # Only settable at creation.
  object_lock_enabled = true

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "logs" {
  count = var.hardened ? 1 : 0

  bucket = aws_s3_bucket.logs[0].id
  versioning_configuration {
    status = "Enabled"
  }
}

# GOVERNANCE, not COMPLIANCE: an admin with s3:BypassGovernanceRetention can still clean up a
# mistake; nobody else can delete or overwrite a log version for 365 days.
resource "aws_s3_bucket_object_lock_configuration" "logs" {
  count = var.hardened ? 1 : 0

  bucket = aws_s3_bucket.logs[0].id
  rule {
    default_retention {
      mode = "GOVERNANCE"
      days = 365
    }
  }

  depends_on = [aws_s3_bucket_versioning.logs]
}

resource "aws_s3_bucket_public_access_block" "logs" {
  count = var.hardened ? 1 : 0

  bucket                  = aws_s3_bucket.logs[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "logs" {
  count = var.hardened ? 1 : 0

  bucket = aws_s3_bucket.logs[0].id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# SSE-S3, because ALB access logs can't be written to a KMS-encrypted bucket. CloudTrail still
# encrypts its own files with the data key (kms_key_id below).
resource "aws_s3_bucket_server_side_encryption_configuration" "logs" {
  count = var.hardened ? 1 : 0

  bucket = aws_s3_bucket.logs[0].id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "logs" {
  count = var.hardened ? 1 : 0

  bucket = aws_s3_bucket.logs[0].id
  rule {
    id     = "archive-then-expire"
    status = "Enabled"
    filter {}

    transition {
      days          = 90
      storage_class = "GLACIER_IR"
    }
    expiration {
      days = 365
    }
    # The expiration above only adds a delete marker; this drops the locked version once it's free.
    noncurrent_version_expiration {
      noncurrent_days = 1
    }
  }

  depends_on = [aws_s3_bucket_versioning.logs]
}

data "aws_elb_service_account" "current" {}

resource "aws_s3_bucket_policy" "logs" {
  count = var.hardened ? 1 : 0

  bucket = aws_s3_bucket.logs[0].id
  policy = data.aws_iam_policy_document.logs_bucket[0].json
}

data "aws_iam_policy_document" "logs_bucket" {
  count = var.hardened ? 1 : 0

  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.logs[0].arn, "${aws_s3_bucket.logs[0].arn}/*"]
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

  # ALB access logs — Seoul predates the log-delivery service principal, so it's ELB's account.
  statement {
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.logs[0].arn}/alb/AWSLogs/${local.account_id}/*"]
    principals {
      type        = "AWS"
      identifiers = [data.aws_elb_service_account.current.arn]
    }
  }

  # CloudTrail, Config and VPC Flow Logs (delivery.logs) each check the bucket, then write under
  # their own prefix — only on behalf of this account.
  statement {
    actions   = ["s3:GetBucketAcl", "s3:ListBucket"]
    resources = [aws_s3_bucket.logs[0].arn]
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com", "config.amazonaws.com", "delivery.logs.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
  }

  # CloudTrail only from this trail, not any trail in the account.
  statement {
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.logs[0].arn}/cloudtrail/AWSLogs/${local.account_id}/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudtrail.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceArn"
      values   = [local.trail_arn]
    }
  }

  statement {
    actions = ["s3:PutObject"]
    resources = [
      "${aws_s3_bucket.logs[0].arn}/config/AWSLogs/${local.account_id}/*",
      "${aws_s3_bucket.logs[0].arn}/vpc-flow/AWSLogs/${local.account_id}/*",
    ]
    principals {
      type        = "Service"
      identifiers = ["config.amazonaws.com", "delivery.logs.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
  }
}

# Every region (an attacker's favourite is the one nobody watches), with digest files to prove the
# logs weren't edited afterwards.
resource "aws_cloudtrail" "main" {
  count = var.hardened ? 1 : 0

  name                          = var.project
  s3_bucket_name                = aws_s3_bucket.logs[0].id
  s3_key_prefix                 = "cloudtrail"
  kms_key_id                    = local.kms_key_arn
  is_multi_region_trail         = true
  include_global_service_events = true
  enable_log_file_validation    = true

  advanced_event_selector {
    name = "management events"
    field_selector {
      field  = "eventCategory"
      equals = ["Management"]
    }
  }

  # Who deleted or overwrote blocks outside the app. Reads are left out: every download is one, and
  # the app already logs who fetched what.
  advanced_event_selector {
    name = "storage bucket writes"
    field_selector {
      field  = "eventCategory"
      equals = ["Data"]
    }
    field_selector {
      field  = "resources.type"
      equals = ["AWS::S3::Object"]
    }
    field_selector {
      field       = "resources.ARN"
      starts_with = ["${aws_s3_bucket.storage.arn}/"]
    }
    field_selector {
      field  = "readOnly"
      equals = ["false"]
    }
  }

  depends_on = [aws_s3_bucket_policy.logs]
}

resource "aws_flow_log" "vpc" {
  count = var.hardened ? 1 : 0

  vpc_id               = module.vpc.vpc_id
  traffic_type         = "ALL"
  log_destination_type = "s3"
  log_destination      = "${aws_s3_bucket.logs[0].arn}/vpc-flow"

  depends_on = [aws_s3_bucket_policy.logs]
}

resource "aws_guardduty_detector" "main" {
  count = var.hardened ? 1 : 0

  enable                       = true
  finding_publishing_frequency = "FIFTEEN_MINUTES"
}

# What GuardDuty watches beyond CloudTrail, VPC flow and DNS: access to the blocks, logins to the
# databases, and processes inside the Fargate tasks (an agent sidecar ECS injects on its own).
resource "aws_guardduty_detector_feature" "main" {
  for_each = var.hardened ? toset(["S3_DATA_EVENTS", "RDS_LOGIN_EVENTS"]) : toset([])

  detector_id = aws_guardduty_detector.main[0].id
  name        = each.key
  status      = "ENABLED"
}

resource "aws_guardduty_detector_feature" "runtime" {
  count = var.hardened ? 1 : 0

  detector_id = aws_guardduty_detector.main[0].id
  name        = "RUNTIME_MONITORING"
  status      = "ENABLED"

  additional_configuration {
    name   = "ECS_FARGATE_AGENT_MANAGEMENT"
    status = "ENABLED"
  }
}

# Security Hub grades the account against these standards, mostly from Config's resource records.
resource "aws_iam_service_linked_role" "config" {
  count = var.hardened ? 1 : 0

  aws_service_name = "config.amazonaws.com"
}

resource "aws_config_configuration_recorder" "main" {
  count = var.hardened ? 1 : 0

  name     = var.project
  role_arn = aws_iam_service_linked_role.config[0].arn

  recording_group {
    all_supported                 = true
    include_global_resource_types = true
  }

  # Every Fargate task start/stop changes an ENI — recorded continuously that's the bulk of the Config
  # bill, and no Security Hub control needs ENIs by the minute.
  recording_mode {
    recording_frequency = "CONTINUOUS"
    recording_mode_override {
      resource_types      = ["AWS::EC2::NetworkInterface"]
      recording_frequency = "DAILY"
    }
  }
}

resource "aws_config_delivery_channel" "main" {
  count = var.hardened ? 1 : 0

  name           = var.project
  s3_bucket_name = aws_s3_bucket.logs[0].id
  s3_key_prefix  = "config"

  depends_on = [aws_config_configuration_recorder.main, aws_s3_bucket_policy.logs]
}

resource "aws_config_configuration_recorder_status" "main" {
  count = var.hardened ? 1 : 0

  name       = aws_config_configuration_recorder.main[0].name
  is_enabled = true

  depends_on = [aws_config_delivery_channel.main]
}

resource "aws_securityhub_account" "main" {
  count = var.hardened ? 1 : 0

  enable_default_standards = false
}

resource "aws_securityhub_standards_subscription" "main" {
  for_each = var.hardened ? toset([
    "arn:aws:securityhub:${var.region}::standards/aws-foundational-security-best-practices/v/1.0.0",
    "arn:aws:securityhub:${var.region}::standards/cis-aws-foundations-benchmark/v/3.0.0",
  ]) : toset([])

  standards_arn = each.key

  depends_on = [aws_securityhub_account.main, aws_config_configuration_recorder_status.main]
}

# Account-wide defaults CIS/FSBP check first (S3.1, IAM.28, EC2.7) — free, one resource each.
resource "aws_s3_account_public_access_block" "main" {
  count = var.hardened ? 1 : 0

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_accessanalyzer_analyzer" "main" {
  count = var.hardened ? 1 : 0

  analyzer_name = var.project
  type          = "ACCOUNT"
}

resource "aws_ebs_encryption_by_default" "main" {
  count = var.hardened ? 1 : 0

  enabled = true
}
