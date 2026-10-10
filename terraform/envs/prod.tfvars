# prod — how this would run for real at the MAU 5M target, built for security, consistency,
# availability and partition tolerance first; cost is not a constraint here. A design to read more
# than a stack that runs today: the sizes are a starting point, not a measurement — tune them from a
# load test and the autoscaling history. terraform plan -var-file=envs/prod.tfvars
#
# Three AZs, so a majority survives losing or being cut off from any one: tasks in private subnets
# behind a NAT per AZ, AWS APIs through VPC endpoints, at least three tasks per service (one per AZ),
# an Aurora cluster per service (writer + two readers, storage quorum across the three AZs).

nat                 = "gateway"
interface_endpoints = true
fargate_spot        = false
container_insights  = true
deletion_protection = true
az_count            = 3

# One Aurora cluster per service: file-service's load (uploads, outbox) never slows the login path,
# and each is sized, failed over and upgraded on its own. A writer and two readers, one per AZ.
db_engine = "aurora"
db_instances = {
  file         = { instance_class = "db.r7g.xlarge", nodes = 3, clients = ["file"] }
  auth         = { instance_class = "db.r7g.large", nodes = 3, clients = ["auth"] }
  member       = { instance_class = "db.r7g.large", nodes = 3, clients = ["member"] }
  notification = { instance_class = "db.r7g.large", nodes = 3, clients = ["notification"] }
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
  gateway      = { cpu = 1024, memory = 2048, min = 3, max = 30 }
  member       = { cpu = 512, memory = 1024, min = 3, max = 12 }
  auth         = { cpu = 1024, memory = 2048, min = 3, max = 30 }
  file         = { cpu = 1024, memory = 2048, min = 3, max = 30 }
  storage      = { cpu = 2048, memory = 4096, min = 3, max = 30 }
  mail         = { cpu = 512, memory = 1024, min = 3, max = 6 }
  notification = { cpu = 512, memory = 1024, min = 3, max = 9 }
}
