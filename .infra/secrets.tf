# Everything a task needs that isn't in its image, as SSM SecureString parameters — free, unlike
# Secrets Manager, which is kept for the RDS master password it rotates (rds.tf, 003-aws-migration.md 1-10).
# ECS hands them to tasks as the env vars the app already reads (${MEMBER_DB_PASSWORD} ...).
locals {
  db_logins = {
    member       = "MEMBER_DB_PASSWORD"
    file         = "FILE_DB_PASSWORD"
    notification = "NOTIFICATION_DB_PASSWORD"
    auth         = "AUTH_DB_PASSWORD"
  }
}

resource "random_password" "db" {
  for_each = local.db_logins

  length  = 32
  special = false
}

resource "aws_ssm_parameter" "db_password" {
  for_each = local.db_logins

  name   = "/${var.project}/${each.value}"
  type   = "SecureString"
  key_id = local.kms_key_arn
  value  = random_password.db[each.key].result
}

# ElastiCache AUTH token, one per cluster: 16–128 chars, no @ " / or spaces.
resource "random_password" "redis" {
  for_each = var.redis_clusters

  length  = 64
  special = false
}

moved {
  from = random_password.redis
  to   = random_password.redis["auth"]
}

# auth keeps the stack's first name; each service gets its cluster's as REDIS_PASSWORD (ecs.tf).
resource "aws_ssm_parameter" "redis_password" {
  for_each = var.redis_clusters

  name   = each.key == "auth" ? "/${var.project}/REDIS_PASSWORD" : "/${var.project}/REDIS_PASSWORD_${upper(each.key)}"
  type   = "SecureString"
  key_id = local.kms_key_arn
  value  = random_password.redis[each.key].result
}

moved {
  from = aws_ssm_parameter.redis_password
  to   = aws_ssm_parameter.redis_password["auth"]
}

# Former AES-256 key for file blocks. Nothing reads it since blocks go to S3 as-is through presigned
# URLs (spec 001 6), and blocks written with it are unreadable to the app (spec 001 1-2: reset). Kept
# only because prevent_destroy refuses a plain removal — once no old block is left anywhere (backup
# copies included), drop the lifecycle blocks in one apply, then the resources in the next.
resource "random_bytes" "storage_encryption_key" {
  length = 32

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ssm_parameter" "storage_encryption_key" {
  name   = "/${var.project}/STORAGE_ENCRYPTION_KEY"
  type   = "SecureString"
  key_id = local.kms_key_arn
  value  = random_bytes.storage_encryption_key.base64

  lifecycle {
    prevent_destroy = true
  }
}

# Discord webhooks are made in Discord, not here: the parameters exist so tasks and Grafana can point
# at them, and their value is set once by hand (aws ssm put-parameter --overwrite) and left alone.
resource "aws_ssm_parameter" "discord_webhook" {
  for_each = toset(["DISCORD_MESSAGING_WEBHOOK_URL", "DISCORD_SERVICE_WEBHOOK_URL"])

  name   = "/${var.project}/${each.key}"
  type   = "SecureString"
  key_id = local.kms_key_arn
  value  = "set-by-hand"

  lifecycle {
    ignore_changes = [value]
  }
}

# Gmail app password for SMTP mail (var.smtp_username), made in the Google account and set by hand
# like the webhooks.
resource "aws_ssm_parameter" "smtp_password" {
  count = var.smtp_username == null ? 0 : 1

  name   = "/${var.project}/SMTP_PASSWORD"
  type   = "SecureString"
  key_id = local.kms_key_arn
  value  = "set-by-hand"

  lifecycle {
    ignore_changes = [value]
  }
}
