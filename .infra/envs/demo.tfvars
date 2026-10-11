# demo — what actually runs, a trial stack: the whole system up for the least money (~$126/month
# in Seoul, before traffic). Cut whatever cost can be cut here; isolation and redundancy are shown in
# prod.tfvars instead. terraform plan -var-file=envs/demo.tfvars
#
# Trade-offs: one NAT instance instead of a managed gateway per AZ — if it stops, running services keep
# serving but events, mail and new tasks wait for it; Spot tasks can be reclaimed and one task per
# service means a short gap on redeploy; no Multi-AZ anywhere.

environment = "demo"
domain_name = "modudrive.help"
account_id  = "569854554527" # this environment's AWS account — set it and other accounts' credentials are refused

# SES production access isn't granted yet (sandbox: verified addresses only), so mail goes through
# Gmail SMTP for now. App password: aws ssm put-parameter --name /modudrive/SMTP_PASSWORD --overwrite.
smtp_username = "modudrive26@gmail.com"
smtp_from     = "modudrive@gmail.com"

nat                 = "instance"
interface_endpoints = false # ~$110/month for six endpoints in two AZs; traffic goes out the IGW
fargate_spot        = true
container_insights  = false
deletion_protection = false # a demo stack gets torn down
hardened            = false
az_count            = 2 # a third AZ would add an ALB public IPv4 and buys nothing for a trial

# One instance for every service's database (each still its own database and login).
db_engine                = "rds"
db_backup_retention_days = 1 # the account is on the AWS free plan, which caps it at 1
db_instances = {
  file = { instance_class = "db.t4g.micro", nodes = 1, clients = ["member", "file", "notification", "auth"] }
}

# One cluster for everything, like local. Accepted for a demo: a burst of uploads shares memory with
# the sessions, and a full Redis (noeviction) fails logins too. prod splits it by purpose.
redis_engine = "elasticache"
redis_clusters = {
  auth = { node_type = "cache.t4g.micro", nodes = 1, clients = ["auth", "member", "mail", "storage"] }
}

# 0.5 vCPU, 1 GB and exactly one task each — no autoscaling, so a traffic burst slows the demo down
# instead of raising the bill. 0.25 vCPU ran out on every request (CPU peaking at 120%), memory stays ~45%.
services = {
  gateway      = { cpu = 512, memory = 1024, min = 1, max = 1 }
  member       = { cpu = 512, memory = 1024, min = 1, max = 1 }
  auth         = { cpu = 512, memory = 1024, min = 1, max = 1 }
  file         = { cpu = 512, memory = 1024, min = 1, max = 1 }
  storage      = { cpu = 512, memory = 1024, min = 1, max = 1 }
  mail         = { cpu = 512, memory = 1024, min = 1, max = 1 }
  notification = { cpu = 512, memory = 1024, min = 1, max = 1 }
}

# A trial account's first alarm. ~$126 of infrastructure + AMP/X-Ray/logs, with headroom.
monthly_budget_usd = 150
alert_email        = null

# The smallest collector; scraping once a minute keeps AMP's per-sample bill near nothing.
otel_collector = { cpu = 256, memory = 1024, scrape_interval = "60s" }
grafana        = false # $9/user/month and IAM Identity Center — AMP, X-Ray and CloudWatch consoles do for a trial

terraform_in_ci = true # a merge into the demo branch applies .infra/ (deploy.yml)
