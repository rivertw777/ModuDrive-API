variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "project" {
  type    = string
  default = "modudrive"
}

# WEB and API must share one registrable domain (app.<domain> / api.<domain>): the session cookie is
# SameSite=Strict + host-only, and *.cloudfront.net / *.elb.amazonaws.com are each their own site
# (aws-migration.md 2-11). Null until a domain is bought — everything domain-bound (Route 53 zone,
# SES identity) is skipped until then.
variable "domain_name" {
  type    = string
  default = null
}

# The image every service runs — CI passes the commit SHA (terraform apply -var image_tag=<sha>), so a
# deploy is a plan you can read. Repositories are immutable: a tag always means the same image.
variable "image_tag" {
  type = string
}

# Sizing — the same architecture at either size; only what costs money and what buys redundancy
# differs. No defaults: every plan names its whole set with -var-file, envs/demo.tfvars (what actually
# runs) or envs/prod.tfvars (sized for the MAU 5M target), never a mix.

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

# RDS PostgreSQL instances, and the services whose database each one holds (rds.tf). Each service's
# database (member_db, file_db ...) lives on exactly one instance.
variable "db_instances" {
  type = map(object({
    instance_class = string
    multi_az       = bool
    clients        = list(string)
  }))

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
variable "redis_clusters" {
  type = map(object({
    node_type = string
    nodes     = number
    clients   = list(string)
  }))

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
