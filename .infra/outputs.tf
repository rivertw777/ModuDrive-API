# Read by step 2 (ECS task definitions) — the env values the app expects.
output "name_servers" {
  description = "Set these at the domain registrar"
  value       = var.domain_name == null ? [] : aws_route53_zone.main[0].name_servers
}

output "postgres_endpoints" {
  description = "Each RDS instance's address, by instance name (var.db_instances)"
  value       = local.db_address
}

output "postgres_master_secret_arns" {
  description = "Admin passwords (Secrets Manager, rotated by RDS) — for postgres_init.sh only"
  value       = local.db_master_secret_arn
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
    { for name, param in aws_ssm_parameter.redis_password : "REDIS_PASSWORD (${name} cluster)" => param.arn },
    {
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

output "db_init_run_tasks" {
  description = "Run each once after the first apply, before the services can start"
  value = { for name, task in aws_ecs_task_definition.db_init : name => join(" ", [
    "aws ecs run-task --cluster ${aws_ecs_cluster.main.name} --launch-type FARGATE",
    "--task-definition ${task.family}",
    "--network-configuration 'awsvpcConfiguration={subnets=[${local.task_subnets[0]}],securityGroups=[${aws_security_group.db_init.id}],assignPublicIp=${local.task_assign_public_ip ? "ENABLED" : "DISABLED"}}'",
  ]) }
}

output "github_deploy_role_arn" {
  description = "AWS_DEPLOY_ROLE_ARN in this environment's GitHub environment"
  value       = aws_iam_role.github_deploy.arn
}

output "amp_endpoint" {
  description = "AMP workspace — the Prometheus data source URL in Grafana"
  value       = aws_prometheus_workspace.main.prometheus_endpoint
}

output "grafana_url" {
  description = "Amazon Managed Grafana (assign IAM Identity Center users to it first)"
  value       = var.grafana ? "https://${aws_grafana_workspace.main[0].endpoint}" : null
}

output "github_terraform_role_arn" {
  description = "AWS_TERRAFORM_ROLE_ARN in the <env>-terraform GitHub environment (terraform_in_ci only)"
  value       = var.terraform_in_ci ? aws_iam_role.github_terraform[0].arn : null
}
