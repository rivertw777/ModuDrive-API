# File blocks (storage-service). The service encrypts blocks itself (STORAGE_ENCRYPTION_KEY); SSE-S3
# below is the bucket default on top. Its task role gets object access only — no CreateBucket (2-7).
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
      sse_algorithm = "AES256"
    }
  }
}

# Blocks are deleted by the app (storage-blocks-purge-requested), never by age — the only lifecycle
# rule cleans up multipart uploads that never completed, which S3 would otherwise bill forever.
resource "aws_s3_bucket_lifecycle_configuration" "storage" {
  bucket = aws_s3_bucket.storage.id

  rule {
    id     = "abort-incomplete-multipart"
    status = "Enabled"
    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}
