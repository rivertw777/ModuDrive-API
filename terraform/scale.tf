# The two sizes this stack runs at (variables.tf `scale`). Same architecture either way — services,
# security groups, queues, DLQs, IAM — only what costs money and what buys redundancy differs.
#
# production numbers are a starting point for MAU 5M, not a measurement: tune them from a load test
# and the autoscaling history before trusting them.
locals {
  scales = {
    # Whole system up for the least money (~$100–150/month in Seoul, before traffic). Trade-offs:
    # tasks sit in public subnets with a public IP instead of behind a NAT — still no inbound except
    # through their security groups, but isolation rests on those groups alone; Spot tasks can be
    # reclaimed and one task per service means a short gap on redeploy; no Multi-AZ anywhere.
    test = {
      nat                 = false
      interface_endpoints = false # ~$110/month for six endpoints in two AZs; traffic goes out the IGW
      fargate_spot        = true
      container_insights  = false
      db_instance_class   = "db.t4g.micro"
      db_multi_az         = false
      redis_node_type     = "cache.t4g.micro"
      redis_nodes         = 1
      deletion_protection = false # a test stack gets torn down
      services = {
        gateway      = { cpu = 512, memory = 1024, min = 1, max = 2 }
        member       = { cpu = 512, memory = 1024, min = 1, max = 2 }
        auth         = { cpu = 512, memory = 1024, min = 1, max = 2 }
        file         = { cpu = 512, memory = 1024, min = 1, max = 2 }
        storage      = { cpu = 512, memory = 1024, min = 1, max = 2 }
        mail         = { cpu = 512, memory = 1024, min = 1, max = 1 }
        notification = { cpu = 512, memory = 1024, min = 1, max = 1 }
      }
    }

    # Tasks in private subnets behind a NAT per AZ, AWS APIs through VPC endpoints, at least two tasks
    # for every user-facing service, RDS Multi-AZ, Valkey with a replica and automatic failover (a lost
    # node no longer logs everyone out).
    production = {
      nat                 = true
      interface_endpoints = true
      fargate_spot        = false
      container_insights  = true
      db_instance_class   = "db.m7g.large"
      db_multi_az         = true
      redis_node_type     = "cache.m7g.large"
      redis_nodes         = 2
      deletion_protection = true
      services = {
        gateway      = { cpu = 1024, memory = 2048, min = 2, max = 10 }
        member       = { cpu = 512, memory = 1024, min = 2, max = 6 }
        auth         = { cpu = 1024, memory = 2048, min = 2, max = 10 }
        file         = { cpu = 1024, memory = 2048, min = 2, max = 10 }
        storage      = { cpu = 1024, memory = 2048, min = 2, max = 10 }
        mail         = { cpu = 512, memory = 1024, min = 1, max = 3 }
        notification = { cpu = 512, memory = 1024, min = 1, max = 4 }
      }
    }
  }

  scale = local.scales[var.scale]

  # Without a NAT, tasks need a public IP to reach ECR, SSM, SQS, SES and Discord.
  task_subnets          = local.scale.nat ? module.vpc.private_subnets : module.vpc.public_subnets
  task_assign_public_ip = !local.scale.nat
}
