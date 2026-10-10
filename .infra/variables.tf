variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "project" {
  type    = string
  default = "modudrive"
}

# Which stack this is — demo or prod. Each lives in its own AWS account with its own state
# bucket (envs/<env>.backend.hcl), so nothing here is named per environment; it only tags resources
# and names the GitHub environment the deploy role trusts (github.tf).
variable "environment" {
  type = string

  validation {
    condition     = contains(["demo", "prod"], var.environment)
    error_message = "environment is demo or prod."
  }
}

# The AWS account this environment lives in. Set, the provider refuses any other account's
# credentials — prod.tfvars can't be planned against the demo account by mistake.
variable "account_id" {
  type    = string
  default = null
}

# WEB and API must share one registrable domain (app.<domain> / api.<domain>): the session cookie is
# SameSite=Strict + host-only, and *.cloudfront.net / *.elb.amazonaws.com are each their own site
# (aws-migration.md 1-11). Null until a domain is bought — everything domain-bound (Route 53 zone,
# SES identity) is skipped until then.
variable "domain_name" {
  type    = string
  default = null
}

# The image the task definitions Terraform registers point at. Only the first apply needs a real one:
# after that the deploy workflow (.github/workflows/deploy.yml) registers new revisions with the
# commit's image and the services ignore Terraform's (ecs.tf). Terraform's revision is then just the
# template the deploy copies — a later apply that changes a task definition (an env var, a size) rolls
# out with the next deploy. CI applies pass image_tag=template so the tag never shows up as a change.
variable "image_tag" {
  type = string
}

# Sizing — the same architecture at every size; only what costs money and what buys redundancy
# differs. No defaults: every plan names its whole set with -var-file — envs/demo.tfvars (the trial
# stack) or envs/prod.tfvars — never a mix.

# How the tasks (always in private subnets) reach the internet — ECR, SSM, SQS, SES, Discord:
#   "gateway"  — a managed NAT gateway per AZ (~$45/month each), nothing to run
#   "instance" — one t4g.nano NAT instance (fck-nat, ~$8/month with its IP), a single point for
#                outbound traffic only — inbound comes through the ALB either way
variable "nat" {
  type = string

  validation {
    condition     = contains(["gateway", "instance"], var.nat)
    error_message = "nat is \"gateway\" or \"instance\"."
  }
}

# VPC interface endpoints for SQS, ECR, CloudWatch Logs, SSM, Secrets Manager.
variable "interface_endpoints" {
  type = bool
}

variable "fargate_spot" {
  type = bool
}

variable "container_insights" {
  type = bool
}

# Availability Zones the stack spreads over. prod uses three: a majority survives losing (or being cut
# off from) any one — Aurora's storage quorum, Valkey replicas and ECS tasks all count on it.
variable "az_count" {
  type = number

  validation {
    condition     = contains([2, 3], var.az_count)
    error_message = "az_count is 2 or 3."
  }
}

# PostgreSQL as plain RDS instances ("rds": nodes = 1 single-AZ, 2 = Multi-AZ with a standby) or Aurora
# clusters ("aurora": a writer and nodes - 1 readers, one per AZ, on storage replicated six ways across
# three AZs; failover in about 30 seconds).
variable "db_engine" {
  type = string

  validation {
    condition     = contains(["rds", "aurora"], var.db_engine)
    error_message = "db_engine is \"rds\" or \"aurora\"."
  }
}

# PostgreSQL instances (or Aurora clusters), and the services whose database each one holds (rds.tf).
# Each service's database (member_db, file_db ...) lives on exactly one.
variable "db_instances" {
  type = map(object({
    instance_class = string
    nodes          = number
    clients        = list(string)
  }))

  validation {
    condition     = alltrue([for i in var.db_instances : i.nodes >= 1 && i.nodes <= (var.db_engine == "rds" ? 2 : 15)])
    error_message = "nodes is 1–2 for rds (2 = Multi-AZ), 1–15 for aurora."
  }

  validation {
    condition = (
      length(flatten([for i in var.db_instances : i.clients])) == length(distinct(flatten([for i in var.db_instances : i.clients])))
      && toset(flatten([for i in var.db_instances : i.clients])) == toset(["member", "file", "notification", "auth"])
    )
    error_message = "member, file, notification and auth each belong to exactly one instance."
  }
}

# RDS deletion protection, and a final snapshot when the database is destroyed anyway.
variable "deletion_protection" {
  type = bool
}

# Valkey clusters by purpose, and the services that use each (redis.tf, spec 006 2-4-6). Every service
# that uses Redis must be a client of exactly one cluster.
# How the Redis clusters run (redis.tf):
#   "elasticache" — ElastiCache for Valkey, one shard; replication to replicas is asynchronous, so a
#                   failover can lose the last writes (a session to log in again, blocks to resend)
#   "memorydb"    — MemoryDB for Valkey, cluster mode: every write is in a Multi-AZ transaction log
#                   before it's acknowledged, so a failover loses nothing — strongly consistent
variable "redis_engine" {
  type = string

  validation {
    condition     = contains(["elasticache", "memorydb"], var.redis_engine)
    error_message = "redis_engine is \"elasticache\" or \"memorydb\"."
  }
}

variable "redis_clusters" {
  type = map(object({
    node_type = string
    # Nodes per shard: a primary and nodes - 1 replicas, each in a different AZ.
    nodes   = number
    shards  = optional(number, 1)
    clients = list(string)
  }))

  validation {
    condition     = var.redis_engine == "memorydb" || alltrue([for c in var.redis_clusters : c.shards == 1])
    error_message = "ElastiCache clusters here have one shard; more shards need memorydb."
  }

  validation {
    condition = (
      length(flatten([for c in var.redis_clusters : c.clients])) == length(distinct(flatten([for c in var.redis_clusters : c.clients])))
      && toset(flatten([for c in var.redis_clusters : c.clients])) == toset(["auth", "member", "mail", "storage"])
    )
    error_message = "auth, member, mail and storage each belong to exactly one cluster."
  }
}

# Every service's task size and autoscaling bounds.
variable "services" {
  type = map(object({
    cpu    = number
    memory = number
    min    = number
    max    = number
  }))
}

# The security extras that cost money (prod): a customer-managed KMS key for data at rest, S3
# versioning, and — in later steps — WAF, Service Connect TLS and audit logging. Free hardening
# (non-root read-only containers, TLS-only S3) applies everywhere regardless.
variable "hardened" {
  type = bool
}

# Monthly AWS budget in USD for this account. Forecast past it or actual past 80% → the alerts topic
# (monitoring.tf), and alert_email if set.
variable "monthly_budget_usd" {
  type = number
}

# Gets the budget alerts by mail too — they still reach Discord without it, but a budget alert is the
# one that must not depend on the stack it's warning about.
variable "alert_email" {
  type    = string
  default = null
}

# Central ADOT collector (monitoring.tf): one task, never more — tail sampling needs every span of a
# trace in one process. scrape_interval is how often it reads each task's /actuator/prometheus.
variable "otel_collector" {
  type = object({
    cpu             = number
    memory          = number
    scrape_interval = string
  })
}

# Amazon Managed Grafana. Needs IAM Identity Center enabled in the account first (its users log in
# through it). Alerts don't depend on it — they're AMP rules (monitoring.tf).
variable "grafana" {
  type = bool
}

# Copies off this account (backup.tf). Both null = backups stay in this account only.
#   backup_copy_vault_arn — an AWS Backup vault in the backup account: the Aurora snapshots go there
#   backup_bucket_arn     — an S3 bucket in the backup account: the storage blocks replicate there,
#                           with backup_bucket_kms_key_arn its encryption key
variable "backup_copy_vault_arn" {
  type    = string
  default = null
}

variable "backup_bucket_arn" {
  type    = string
  default = null
}

variable "backup_bucket_kms_key_arn" {
  type    = string
  default = null
}

# The repository whose deploy workflow may assume the deploy role (github.tf).
variable "github_repository" {
  type    = string
  default = "rivertw777/ModuDrive-API"
}

# CI applies .infra/ for this environment (github.tf, .github/workflows/deploy.yml): demo, on a
# merge into the demo branch. Elsewhere apply stays a person's job and no admin role trusts GitHub.
variable "terraform_in_ci" {
  type = bool
}
