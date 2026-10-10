# demo — what actually runs: the whole system up for the least money (~$100–150/month in Seoul,
# before traffic). terraform plan -var-file=envs/demo.tfvars
#
# Trade-offs: tasks sit in public subnets with a public IP instead of behind a NAT — still no inbound
# except through their security groups, but isolation rests on those groups alone; Spot tasks can be
# reclaimed and one task per service means a short gap on redeploy; no Multi-AZ anywhere.

nat                 = false
interface_endpoints = false # ~$110/month for six endpoints in two AZs; traffic goes out the IGW
fargate_spot        = true
container_insights  = false
db_instance_class   = "db.t4g.micro"
db_multi_az         = false
deletion_protection = false # a demo stack gets torn down

# Two clusters: storage-service's uploads stay out of the sessions' memory, everything else shares one.
redis_clusters = {
  auth    = { node_type = "cache.t4g.micro", nodes = 1, clients = ["auth", "member", "mail"] }
  storage = { node_type = "cache.t4g.micro", nodes = 1, clients = ["storage"] }
}

services = {
  gateway      = { cpu = 512, memory = 1024, min = 1, max = 2 }
  member       = { cpu = 512, memory = 1024, min = 1, max = 2 }
  auth         = { cpu = 512, memory = 1024, min = 1, max = 2 }
  file         = { cpu = 512, memory = 1024, min = 1, max = 2 }
  storage      = { cpu = 512, memory = 1024, min = 1, max = 2 }
  mail         = { cpu = 512, memory = 1024, min = 1, max = 1 }
  notification = { cpu = 512, memory = 1024, min = 1, max = 1 }
}
