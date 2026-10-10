# Copies off this account (aws-migration.md 4-7). Everything else here — Aurora's 35 days, S3 versions,
# MemoryDB snapshots — lives in the same account and region as the data, so an account takeover or a
# region-wide loss takes the backups with it. These two send a copy to a separate backup account:
#   - Aurora/RDS: a daily AWS Backup snapshot, copied to a vault there (backup_copy_vault_arn)
#   - storage blocks: S3 replication to a bucket there (backup_bucket_arn), Glacier IR
# Both need the backup account set up first (aws-migration.md 4-7). MemoryDB isn't covered: AWS Backup
# doesn't take it, and what it holds (sessions, codes, upload records) is re-creatable — a restore
# logs everyone out. The block key, STORAGE_ENCRYPTION_KEY, is copied by hand once (same section).
locals {
  backup_copy   = var.backup_copy_vault_arn != null
  backup_bucket = var.backup_bucket_arn != null
  # Both name the backup account in their ARN (arn:aws:<service>:<region>:<account>:...).
  backup_account_id = (
    local.backup_copy ? split(":", var.backup_copy_vault_arn)[4]
    : local.backup_bucket ? split(":", var.backup_bucket_kms_key_arn)[4] : null
  )
}

resource "aws_backup_vault" "main" {
  count = local.backup_copy ? 1 : 0

  name        = var.project
  kms_key_arn = local.kms_key_arn
}

resource "aws_iam_role" "backup" {
  count = local.backup_copy ? 1 : 0

  name = "${var.project}-backup"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "backup.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "backup" {
  for_each = local.backup_copy ? toset([
    "arn:aws:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForBackup",
    "arn:aws:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForRestores",
  ]) : toset([])

  role       = aws_iam_role.backup[0].name
  policy_arn = each.value
}

# Daily, after Aurora's own backup window (rds.tf). A week here — point-in-time restore within 35 days
# is Aurora's job — and 90 days in the backup account.
resource "aws_backup_plan" "main" {
  count = local.backup_copy ? 1 : 0

  name = var.project

  rule {
    rule_name         = "daily"
    target_vault_name = aws_backup_vault.main[0].name
    schedule          = "cron(0 20 * * ? *)" # 05:00 KST
    start_window      = 60
    completion_window = 360

    lifecycle {
      delete_after = 7
    }

    copy_action {
      destination_vault_arn = var.backup_copy_vault_arn
      lifecycle {
        delete_after = 90
      }
    }
  }
}

resource "aws_backup_selection" "databases" {
  count = local.backup_copy ? 1 : 0

  name         = "databases"
  plan_id      = aws_backup_plan.main[0].id
  iam_role_arn = aws_iam_role.backup[0].arn
  resources = concat(
    [for c in aws_rds_cluster.postgres : c.arn],
    [for i in aws_db_instance.postgres : i.arn],
  )
}

# ---------------------------------------------------------------------------------------------------
# Block replication. Deletes don't replicate (delete markers stay here), so a purge — by the app, a bug
# or an intruder — leaves the copy whole; how long the copy keeps blocks is the backup account's
# lifecycle rule. The copy is still ciphertext without STORAGE_ENCRYPTION_KEY.
resource "aws_iam_role" "replication" {
  count = local.backup_bucket ? 1 : 0

  name = "${var.project}-s3-replication"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "s3.amazonaws.com" }
    }]
  })
}

data "aws_iam_policy_document" "replication" {
  count = local.backup_bucket ? 1 : 0

  statement {
    actions   = ["s3:GetReplicationConfiguration", "s3:ListBucket"]
    resources = [aws_s3_bucket.storage.arn]
  }
  statement {
    actions   = ["s3:GetObjectVersionForReplication", "s3:GetObjectVersionAcl", "s3:GetObjectVersionTagging"]
    resources = ["${aws_s3_bucket.storage.arn}/*"]
  }
  statement {
    actions   = ["s3:ReplicateObject", "s3:ReplicateTags", "s3:ObjectOwnerOverrideToBucketOwner"]
    resources = ["${var.backup_bucket_arn}/*"]
  }
  statement {
    actions   = ["kms:Decrypt"]
    resources = compact([local.kms_key_arn]) # null without hardened — the precondition below says why
  }
  statement {
    actions   = ["kms:Encrypt", "kms:GenerateDataKey"]
    resources = [var.backup_bucket_kms_key_arn]
  }
}

resource "aws_iam_role_policy" "replication" {
  count = local.backup_bucket ? 1 : 0

  role   = aws_iam_role.replication[0].id
  policy = data.aws_iam_policy_document.replication[0].json
}

resource "aws_s3_bucket_replication_configuration" "storage" {
  count = local.backup_bucket ? 1 : 0

  bucket = aws_s3_bucket.storage.id
  role   = aws_iam_role.replication[0].arn

  rule {
    id     = "backup-account"
    status = "Enabled"
    filter {}

    delete_marker_replication {
      status = "Disabled"
    }

    source_selection_criteria {
      sse_kms_encrypted_objects {
        status = "Enabled"
      }
    }

    destination {
      bucket        = var.backup_bucket_arn
      account       = local.backup_account_id
      storage_class = "GLACIER_IR"

      access_control_translation {
        owner = "Destination"
      }

      encryption_configuration {
        replica_kms_key_id = var.backup_bucket_kms_key_arn
      }
    }
  }

  # Replication needs versioning, and the KMS-only rule above the customer key — both come with hardened.
  lifecycle {
    precondition {
      condition     = var.hardened && var.backup_bucket_kms_key_arn != null
      error_message = "Block replication needs hardened = true (versioning) and backup_bucket_kms_key_arn."
    }
  }

  depends_on = [aws_s3_bucket_versioning.storage]
}
