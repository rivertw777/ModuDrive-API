# demo — what actually runs, a trial stack: the whole system up for the least money (~$138/month
# in Seoul, before traffic). Cut whatever cost can be cut here; isolation and redundancy are shown in
# prod.tfvars instead. terraform plan -var-file=envs/demo.tfvars
#
# Trade-offs: one NAT instance instead of a managed gateway per AZ — if it stops, running services keep
# serving but events, mail and new tasks wait for it; Spot tasks can be reclaimed and one task per
# service means a short gap on redeploy; no Multi-AZ anywhere.

environment = "demo"
domain_name = "modudrive.help"
account_id  = "569854554527" # this environment's AWS account — set it and other accounts' credentials are refused

nat                 = "instance"
interface_endpoints = false # ~$110/month for six endpoints in two AZs; traffic goes out the NAT instance
fargate_spot        = true
container_insights  = false
deletion_protection = false # a demo stack gets torn down
hardened            = false
az_count            = 2 # a third AZ would add an ALB public IPv4 and buys nothing for a trial

# One instance for every service's database (each still its own database and login).
db_engine                = "rds"
db_backup_retention_days = 1 # the account is on the AWS free plan, which caps it at 1
# 4 services x Hikari's default 10 kept 40 backends open on a 1 GB micro: ~60 MB free and swapping.
# 5 each, always open: 20 with one task per service, 40 if all four scale to two.
db_pool_size = 5
db_instances = {
  file = { instance_class = "db.t4g.micro", nodes = 1, clients = ["member", "file", "notification", "auth"] }
}

# One cluster for everything, like local. Accepted for a demo: a burst of uploads shares memory with
# the sessions, and a full Redis (noeviction) fails logins too. prod splits it by purpose.
redis_engine = "elasticache"
redis_clusters = {
  auth = { node_type = "cache.t4g.micro", nodes = 1, clients = ["auth", "member", "mail", "storage"] }
}

# 0.5 vCPU, 1 GB, one task each and at most two — a burst gets one extra task (CPU above 60%), past
# that it slows the demo down instead of raising the bill further. 0.25 vCPU ran out on every request
# (CPU peaking at 120%), memory stays ~45%. DB connections: db_pool_size above.
services = {
  gateway      = { cpu = 512, memory = 1024, min = 1, max = 2 }
  member       = { cpu = 512, memory = 1024, min = 1, max = 2 }
  auth         = { cpu = 512, memory = 1024, min = 1, max = 2 }
  file         = { cpu = 512, memory = 1024, min = 1, max = 2 }
  storage      = { cpu = 512, memory = 1024, min = 1, max = 2 }
  mail         = { cpu = 512, memory = 1024, min = 1, max = 2 }
  notification = { cpu = 512, memory = 1024, min = 1, max = 2 }
}

# A trial account's first alarm. ~$138 of infrastructure + logs, with headroom.
monthly_budget_usd = 150
alert_email        = null

# The smallest collector, scraping once a minute.
otel_collector = { cpu = 256, memory = 1024, scrape_interval = "60s" }
grafana        = false # $9/user/month and IAM Identity Center — Grafana Cloud's free tier below instead

# Grafana Cloud free tier (10k metric series, 50 GB logs and traces a month, 14 days kept) in place of
# AMP, X-Ray and Managed Grafana: one Grafana with metrics, logs and traces linked, like local.
grafana_cloud = {
  otlp_url          = "https://otlp-gateway-prod-ap-northeast-0.grafana.net/otlp"
  otlp_user         = "1890952"
  prometheus_url    = "https://prometheus-prod-49-prod-ap-northeast-0.grafana.net"
  prometheus_user   = "3706127"
  alertmanager_url  = "https://alertmanager-prod-ap-northeast-0.grafana.net"
  alertmanager_user = "1850999"
}

terraform_in_ci = true # a merge into the demo branch applies .infra/ (deploy.yml)
