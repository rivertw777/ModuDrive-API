data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}

# Public subnets hold the ALB and the NAT gateways; ECS tasks run in the private subnets in prod
# and in the public ones in demo (no NAT — envs/demo.tfvars). RDS and ElastiCache are always private.
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.7"

  name = var.project
  cidr = "10.0.0.0/16"
  azs  = local.azs

  public_subnets   = ["10.0.0.0/24", "10.0.1.0/24"]
  private_subnets  = ["10.0.10.0/23", "10.0.12.0/23"]
  database_subnets = ["10.0.20.0/24", "10.0.21.0/24"]

  # One per AZ, so an AZ outage doesn't cut the other AZ's egress (SES, Discord). ~$45/month each.
  enable_nat_gateway     = var.nat
  one_nat_gateway_per_az = var.nat

  enable_dns_hostnames = true
  enable_dns_support   = true

  create_database_subnet_group = true
}

# AWS APIs the tasks call all day go through endpoints instead of the NAT, which bills per GB.
# S3 is a gateway endpoint (free); the rest are interface endpoints (hourly per AZ).
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = module.vpc.vpc_id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  # Both, so S3 traffic skips the NAT/IGW wherever the tasks run (var.nat).
  route_table_ids = concat(module.vpc.private_route_table_ids, module.vpc.public_route_table_ids)
}

resource "aws_vpc_endpoint" "interface" {
  # ecr.api/ecr.dkr pull images, logs takes awslogs output, ssm/secretsmanager hand secrets to tasks.
  for_each = var.interface_endpoints ? toset(["sqs", "ecr.api", "ecr.dkr", "logs", "ssm", "secretsmanager"]) : toset([])

  vpc_id              = module.vpc.vpc_id
  service_name        = "com.amazonaws.${var.region}.${each.key}"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = module.vpc.private_subnets
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true
}

locals {
  # Without a NAT, tasks need a public IP to reach ECR, SSM, SQS, SES and Discord.
  task_subnets          = var.nat ? module.vpc.private_subnets : module.vpc.public_subnets
  task_assign_public_ip = !var.nat
}
