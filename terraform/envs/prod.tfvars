# prod — how this would run for real, sized for the MAU 5M target. A design to read more than a stack
# that runs today: the numbers are a starting point, not a measurement — tune them from a load test
# and the autoscaling history before trusting them. terraform plan -var-file=envs/prod.tfvars
#
# Tasks in private subnets behind a NAT per AZ, AWS APIs through VPC endpoints, at least two tasks for
# every user-facing service, RDS Multi-AZ, one Valkey cluster per purpose with the sessions' keeping a
# replica and automatic failover (a lost node no longer logs everyone out).

nat                 = true
interface_endpoints = true
fargate_spot        = false
container_insights  = true
deletion_protection = true

# One instance per service: file-service's load (uploads, outbox) never slows the login path, and each
# is sized, failed over and upgraded on its own. All Multi-AZ.
db_instances = {
  file         = { instance_class = "db.m7g.large", multi_az = true, clients = ["file"] }
  auth         = { instance_class = "db.m7g.large", multi_az = true, clients = ["auth"] }
  member       = { instance_class = "db.t4g.medium", multi_az = true, clients = ["member"] }
  notification = { instance_class = "db.t4g.medium", multi_az = true, clients = ["notification"] }
}

# One cluster per purpose: a load spike or a full memory in one never reaches the others. Only the
# sessions get a replica — a lost member/mail/storage node costs a resent code, a possibly repeated
# mail, or uploads in flight sending their blocks again; nobody is logged out.
redis_clusters = {
  auth    = { node_type = "cache.m7g.large", nodes = 2, clients = ["auth"] }
  member  = { node_type = "cache.t4g.small", nodes = 1, clients = ["member"] }
  mail    = { node_type = "cache.t4g.small", nodes = 1, clients = ["mail"] }
  storage = { node_type = "cache.m7g.large", nodes = 1, clients = ["storage"] }
}

services = {
  gateway      = { cpu = 1024, memory = 2048, min = 2, max = 10 }
  member       = { cpu = 512, memory = 1024, min = 2, max = 6 }
  auth         = { cpu = 1024, memory = 2048, min = 2, max = 10 }
  file         = { cpu = 1024, memory = 2048, min = 2, max = 10 }
  storage      = { cpu = 1024, memory = 2048, min = 2, max = 10 }
  mail         = { cpu = 512, memory = 1024, min = 1, max = 3 }
  notification = { cpu = 512, memory = 1024, min = 1, max = 4 }
}
