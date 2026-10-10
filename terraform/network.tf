data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)
}

# Public subnets hold the ALB and the NAT (gateways in prod, one instance in demo); ECS tasks, RDS and
# ElastiCache are always in private subnets.
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.7"

  name = var.project
  cidr = "10.0.0.0/16"
  azs  = local.azs

  # One of each per AZ in use (var.az_count); the third goes unused at two.
  public_subnets   = slice(["10.0.0.0/24", "10.0.1.0/24", "10.0.2.0/24"], 0, var.az_count)
  private_subnets  = slice(["10.0.10.0/23", "10.0.12.0/23", "10.0.14.0/23"], 0, var.az_count)
  database_subnets = slice(["10.0.20.0/24", "10.0.21.0/24", "10.0.22.0/24"], 0, var.az_count)

  # One per AZ, so an AZ outage doesn't cut the other AZ's egress (SES, Discord). ~$45/month each.
  # Without them the module still makes a private route table per AZ, routed below to the instance.
  enable_nat_gateway     = var.nat == "gateway"
  one_nat_gateway_per_az = var.nat == "gateway"

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
  # Both, so S3 traffic (blocks) never goes through the NAT, gateway or instance.
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

# demo's NAT: one fck-nat instance (an Amazon Linux image set up to forward and masquerade) instead
# of a managed gateway per AZ — ~$8/month against the ~$26 the tasks' own public IPv4s would cost.
# Only outbound control traffic passes here (SQS, SES, logs, secrets, image pulls); user traffic comes
# in through the ALB and S3 through its gateway endpoint. If it stops, running services keep serving,
# but events and mail wait and new tasks can't pull their image until it's back (EC2 auto-recovery).
# ponytail: one instance in one AZ — fck-nat's HA mode (ASG + static ENI) if demo ever needs it.
data "aws_ami" "fck_nat" {
  count = var.nat == "instance" ? 1 : 0

  most_recent = true
  owners      = ["568608671756"] # fck-nat

  filter {
    name   = "name"
    values = ["fck-nat-al2023-*-arm64-ebs"]
  }
}

resource "aws_security_group" "nat" {
  count = var.nat == "instance" ? 1 : 0

  name        = "${var.project}-nat"
  description = "NAT instance: forwards the VPC's outbound traffic"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "nat" {
  count = var.nat == "instance" ? 1 : 0

  security_group_id = aws_security_group.nat[0].id
  cidr_ipv4         = module.vpc.vpc_cidr_block
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_egress_rule" "nat" {
  count = var.nat == "instance" ? 1 : 0

  security_group_id = aws_security_group.nat[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_instance" "nat" {
  count = var.nat == "instance" ? 1 : 0

  ami                         = data.aws_ami.fck_nat[0].id
  instance_type               = "t4g.nano"
  subnet_id                   = module.vpc.public_subnets[0]
  vpc_security_group_ids      = [aws_security_group.nat[0].id]
  associate_public_ip_address = true
  # It forwards packets addressed to others.
  source_dest_check = false

  metadata_options {
    http_tokens = "required"
  }

  tags = { Name = "${var.project}-nat" }
}

resource "aws_route" "private_nat_instance" {
  count = var.nat == "instance" ? length(module.vpc.private_route_table_ids) : 0

  route_table_id         = module.vpc.private_route_table_ids[count.index]
  destination_cidr_block = "0.0.0.0/0"
  network_interface_id   = aws_instance.nat[0].primary_network_interface_id
}

locals {
  # Tasks never get a public IP: their way out is the NAT, gateway or instance.
  task_subnets          = module.vpc.private_subnets
  task_assign_public_ip = false
}
