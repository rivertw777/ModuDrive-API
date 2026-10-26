# Everything a task needs that isn't in its image, as SSM SecureString parameters — free, unlike
# Secrets Manager, which is kept for the RDS master password it rotates (rds.tf, 002-aws-migration.md 1-10).
# ECS hands them to tasks as env vars: SPRING_DATASOURCE_PASSWORD / REDIS_PASSWORD to the services,
# ${MEMBER_DB_PASSWORD} ... to db-init.
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

# Discord webhooks are made in Discord, not here: the parameters exist so the Discord forwarder Lambda and
# Grafana can point at them, and their value is set once by hand (aws ssm put-parameter --overwrite) and left alone.
resource "aws_ssm_parameter" "discord_webhook" {
  for_each = toset([for c in ["MESSAGING", "SERVICE", "INFRA", "ACCOUNT"] : "DISCORD_${c}_WEBHOOK_URL"])

  name   = "/${var.project}/${each.key}"
  type   = "SecureString"
  key_id = local.kms_key_arn
  value  = "set-by-hand"

  lifecycle {
    ignore_changes = [value]
  }
}

# The ping URL of the heartbeat check (healthchecks.io), made there and set by hand like the webhooks. The
# Watchdog alert pings it every 5 minutes; when the pings stop, healthchecks.io alerts on its own.
resource "aws_ssm_parameter" "heartbeat_url" {
  name   = "/${var.project}/HEARTBEAT_URL"
  type   = "SecureString"
  key_id = local.kms_key_arn
  value  = "set-by-hand"

  lifecycle {
    ignore_changes = [value]
  }
}
