# File blocks (storage-service). The service encrypts blocks itself (STORAGE_ENCRYPTION_KEY); the
# bucket encrypts again on top — with the customer KMS key when hardened (prod), SSE-S3 otherwise. Its task role gets object access only — no CreateBucket (2-7).
resource "aws_s3_bucket" "storage" {
  bucket = "${var.project}-storage-${data.aws_caller_identity.current.account_id}"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_public_access_block" "storage" {
  bucket = aws_s3_bucket.storage.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "storage" {
  bucket = aws_s3_bucket.storage.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = var.hardened ? "aws:kms" : "AES256"
      kms_master_key_id = local.kms_key_arn
    }
    # One data key per object batch instead of a KMS call per block.
    bucket_key_enabled = var.hardened
  }
}

# TLS only, everywhere: a request over plain HTTP is refused whoever makes it.
resource "aws_s3_bucket_policy" "storage" {
  bucket = aws_s3_bucket.storage.id
  policy = data.aws_iam_policy_document.storage_bucket.json
}

data "aws_iam_policy_document" "storage_bucket" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.storage.arn, "${aws_s3_bucket.storage.arn}/*"]
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

# prod keeps what's deleted or overwritten for 30 days: a block purged by mistake (or by a bug) can
# still be restored. S3 drops the old versions after that (lifecycle below).
resource "aws_s3_bucket_versioning" "storage" {
  count = var.hardened ? 1 : 0

  bucket = aws_s3_bucket.storage.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Blocks are deleted by the app (storage-blocks-purge-requested), never by age — the only lifecycle
# rule cleans up multipart uploads that never completed, which S3 would otherwise bill forever.
resource "aws_s3_bucket_lifecycle_configuration" "storage" {
  bucket = aws_s3_bucket.storage.id
  # The noncurrent-version rule needs versioning on first.
  depends_on = [aws_s3_bucket_versioning.storage]

  rule {
    id     = "abort-incomplete-multipart"
    status = "Enabled"
    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  dynamic "rule" {
    for_each = var.hardened ? [1] : []
    content {
      id     = "expire-old-versions"
      status = "Enabled"
      filter {}

      noncurrent_version_expiration {
        noncurrent_days = 30
      }
      expiration {
        expired_object_delete_marker = true
      }
    }
  }
}
