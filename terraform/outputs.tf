# Read by step 2 (ECS task definitions) — the env values the app expects.
output "name_servers" {
  description = "Set these at the domain registrar"
  value       = var.domain_name == null ? [] : aws_route53_zone.main[0].name_servers
}

output "postgres_endpoint" {
  value = aws_db_instance.postgres.address
}

output "postgres_master_secret_arn" {
  description = "Admin password (Secrets Manager, rotated by RDS) — for postgres_init.sh only"
  value       = aws_db_instance.postgres.master_user_secret[0].secret_arn
}

output "redis_endpoint" {
  description = "REDIS_HOST (with REDIS_SSL_ENABLED=true)"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "storage_bucket" {
  description = "STORAGE_S3_BUCKET"
  value       = aws_s3_bucket.storage.bucket
}

output "ssm_parameter_arns" {
  description = "Secrets for ECS task definitions, by env var name"
  value = merge(
    { for key, param in aws_ssm_parameter.db_password : local.db_logins[key] => param.arn },
    { for name, param in aws_ssm_parameter.discord_webhook : name => param.arn },
    {
      REDIS_PASSWORD         = aws_ssm_parameter.redis_password.arn
      STORAGE_ENCRYPTION_KEY = aws_ssm_parameter.storage_encryption_key.arn
    },
  )
}
