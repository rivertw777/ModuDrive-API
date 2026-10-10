# demo — what actually runs, a trial stack: the whole system up for the least money (~$108/month
# in Seoul, before traffic). Cut whatever cost can be cut here; isolation and redundancy are shown in
# prod.tfvars instead. terraform plan -var-file=envs/demo.tfvars
#
# Trade-offs: one NAT instance instead of a managed gateway per AZ — if it stops, running services keep
# serving but events, mail and new tasks wait for it; Spot tasks can be reclaimed and one task per
# service means a short gap on redeploy; no Multi-AZ anywhere.

nat                 = "instance"
interface_endpoints = false # ~$110/month for six endpoints in two AZs; traffic goes out the IGW
fargate_spot        = true
container_insights  = false
deletion_protection = false # a demo stack gets torn down
hardened            = false
az_count            = 2 # a third AZ would add an ALB public IPv4 and buys nothing for a trial

# One instance for every service's database (each still its own database and login).
db_engine = "rds"
db_instances = {
  file = { instance_class = "db.t4g.micro", nodes = 1, clients = ["member", "file", "notification", "auth"] }
}

# One cluster for everything, like local. Accepted for a demo: a burst of uploads shares memory with
# the sessions, and a full Redis (noeviction) fails logins too. prod splits it by purpose.
redis_engine = "elasticache"
redis_clusters = {
  auth = { node_type = "cache.t4g.micro", nodes = 1, clients = ["auth", "member", "mail", "storage"] }
}

# The smallest Fargate size a JVM runs in (0.25 vCPU, 1 GB) and exactly one task each — no autoscaling,
# so a traffic burst slows the demo down instead of raising the bill. Slower start (ecs.tf startPeriod).
services = {
  gateway      = { cpu = 256, memory = 1024, min = 1, max = 1 }
  member       = { cpu = 256, memory = 1024, min = 1, max = 1 }
  auth         = { cpu = 256, memory = 1024, min = 1, max = 1 }
  file         = { cpu = 256, memory = 1024, min = 1, max = 1 }
  storage      = { cpu = 256, memory = 1024, min = 1, max = 1 }
  mail         = { cpu = 256, memory = 1024, min = 1, max = 1 }
  notification = { cpu = 256, memory = 1024, min = 1, max = 1 }
}
