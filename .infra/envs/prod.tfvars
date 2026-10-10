# prod — how this would run for real at the MAU 5M target, built for security, consistency,
# availability and partition tolerance first, then sized down to what that needs. A design to read more
# than a stack that runs today: the sizes are a starting point, not a measurement — tune them from a
# load test and the autoscaling history. terraform plan -var-file=envs/prod.tfvars
#
# Three AZs, so a majority survives losing or being cut off from any one: tasks in private subnets
# behind a NAT per AZ, AWS APIs through VPC endpoints, at least two tasks per service, an Aurora
# cluster per service (writer + one reader; storage keeps its quorum across the three AZs regardless).
# Roughly $5,800/month on demand before traffic (aws-migration.md 4-3).

environment = "prod"
account_id  = null # this environment's AWS account — set it and other accounts' credentials are refused

nat                 = "gateway"
interface_endpoints = true
fargate_spot        = false
container_insights  = true
deletion_protection = true
hardened            = true
az_count            = 3

# One Aurora cluster per service: file-service's load (uploads, outbox) never slows the login path,
# and each is sized, failed over and upgraded on its own. A writer and one reader: Aurora's storage is
# six copies across three AZs whatever the instance count, so the reader only buys a ~30s failover
# instead of a rebuilt instance — a second reader would buy nothing the services use (writer-only reads).
db_engine = "aurora"
db_instances = {
  file         = { instance_class = "db.r7g.xlarge", nodes = 2, clients = ["file"] }
  auth         = { instance_class = "db.r7g.large", nodes = 2, clients = ["auth"] }
  member       = { instance_class = "db.r7g.large", nodes = 2, clients = ["member"] }
  notification = { instance_class = "db.r7g.large", nodes = 2, clients = ["notification"] }
}

# MemoryDB, one cluster per purpose: a load spike or a full memory in one never reaches the others,
# and no write is lost on a failover (the Multi-AZ transaction log, not the replicas, holds it). A
# primary and one replica per shard. member (sign-up codes) and mail (idempotency keys) hold little and
# run on t4g; storage's upload records (up to 25,600 keys per user a day) spread over two shards.
redis_engine = "memorydb"
redis_clusters = {
  auth    = { node_type = "db.r7g.large", nodes = 2, clients = ["auth"] }
  member  = { node_type = "db.t4g.medium", nodes = 2, clients = ["member"] }
  mail    = { node_type = "db.t4g.medium", nodes = 2, clients = ["mail"] }
  storage = { node_type = "db.r7g.xlarge", nodes = 2, shards = 2, clients = ["storage"] }
}

services = {
  gateway      = { cpu = 1024, memory = 2048, min = 3, max = 30 }
  member       = { cpu = 512, memory = 1024, min = 2, max = 12 }
  auth         = { cpu = 1024, memory = 2048, min = 3, max = 30 }
  file         = { cpu = 1024, memory = 2048, min = 3, max = 30 }
  storage      = { cpu = 2048, memory = 4096, min = 3, max = 30 }
  mail         = { cpu = 512, memory = 1024, min = 2, max = 6 }
  notification = { cpu = 512, memory = 1024, min = 2, max = 9 }
}

monthly_budget_usd = 8000
alert_email        = null

otel_collector = { cpu = 1024, memory = 2048, scrape_interval = "30s" }
grafana        = true

# Copies to the backup account (backup.tf, aws-migration.md 4-7) — made there first.
backup_copy_vault_arn     = null # arn:aws:backup:ap-northeast-2:<backup-account>:backup-vault:modudrive-prod
backup_bucket_arn         = null # arn:aws:s3:::modudrive-prod-blocks-<backup-account>
backup_bucket_kms_key_arn = null # arn:aws:kms:ap-northeast-2:<backup-account>:key/...

terraform_in_ci = false # apply by hand — no admin role trusts GitHub here
