# staging — prod's shape at the smallest size, in its own account: the place to try what demo can't
# show before it reaches prod — Aurora and MemoryDB (cluster mode), the customer KMS key, S3 versioning,
# WAF, Service Connect TLS, GuardDuty/Config. One of each, no redundancy, Spot tasks; roughly
# $300/month before traffic, most of it the fixed hardened extras (private CA ~$50, WAF, Config).
# terraform plan -var-file=envs/staging.tfvars

environment = "staging"
account_id  = null # this environment's AWS account — set it and other accounts' credentials are refused

nat                 = "instance"
interface_endpoints = false
fargate_spot        = true
container_insights  = false
deletion_protection = false
hardened            = true
az_count            = 2

# prod's engines, one small node each. All four databases share one cluster ("file" keeps the stack's
# first identifier) — the per-service split is just more of the same, and prod.tfvars shows it.
db_engine = "aurora"
db_instances = {
  file = { instance_class = "db.t4g.medium", nodes = 1, clients = ["member", "file", "notification", "auth"] }
}

redis_engine = "memorydb"
redis_clusters = {
  auth = { node_type = "db.t4g.small", nodes = 1, clients = ["auth", "member", "mail", "storage"] }
}

services = {
  gateway      = { cpu = 256, memory = 1024, min = 1, max = 1 }
  member       = { cpu = 256, memory = 1024, min = 1, max = 1 }
  auth         = { cpu = 256, memory = 1024, min = 1, max = 1 }
  file         = { cpu = 256, memory = 1024, min = 1, max = 1 }
  storage      = { cpu = 256, memory = 1024, min = 1, max = 1 }
  mail         = { cpu = 256, memory = 1024, min = 1, max = 1 }
  notification = { cpu = 256, memory = 1024, min = 1, max = 1 }
}

monthly_budget_usd = 400
alert_email        = null

otel_collector = { cpu = 256, memory = 1024, scrape_interval = "60s" }
grafana        = false
