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

output "redis_hosts" {
  description = "REDIS_HOST per service (with REDIS_SSL_ENABLED=true)"
  value       = local.redis_host
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

output "alb_dns_name" {
  description = "Without a domain, the stack answers here over plain HTTP"
  value       = aws_lb.main.dns_name
}

output "ecr_repository_urls" {
  description = "Where CI pushes each service's image"
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}

output "db_init_run_task" {
  description = "Run once after the first apply, before the services can start"
  value = join(" ", [
    "aws ecs run-task --cluster ${aws_ecs_cluster.main.name} --launch-type FARGATE",
    "--task-definition ${aws_ecs_task_definition.db_init.family}",
    "--network-configuration 'awsvpcConfiguration={subnets=[${local.task_subnets[0]}],securityGroups=[${aws_security_group.db_init.id}],assignPublicIp=${local.task_assign_public_ip ? "ENABLED" : "DISABLED"}}'",
  ])
}
