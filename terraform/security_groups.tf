# One security group per service; inbound only from the callers that really call it
# (aws-migration.md 2-13). /internal/** has no auth of its own — these rules are what keeps, say, a
# compromised gateway away from member's internal APIs. Add a Feign/WebClient call → add its caller
# here, or it times out only on AWS (compose has everything open).
locals {
  service_ports = {
    gateway      = 10001
    member       = 10010
    auth         = 10011
    file         = 10012
    storage      = 10013
    mail         = 10014
    notification = 10015
  }

  # service => who may call its app port. mail has no HTTP API (SQS only), so nothing.
  service_callers = {
    gateway      = ["alb"]
    auth         = ["gateway"]                 # route + session check (AuthClient)
    member       = ["gateway", "auth", "file"] # route / login check / share target lookup
    file         = ["gateway", "storage"]      # route / versions, zip entries, committed blocks
    storage      = ["gateway", "file"]         # route / uploaded blocks on commit (block purge is SQS)
    notification = ["gateway"]                 # route
    mail         = []
  }

  service_ingress = merge([
    for service, callers in local.service_callers : {
      for caller in callers : "${caller}-to-${service}" => { service = service, caller = caller }
    }
  ]...)

  # Data stores: only the services that use them.
  postgres_clients = ["member", "file", "notification", "auth"]
  redis_clients    = ["auth", "member", "storage", "mail"]
}

resource "aws_security_group" "alb" {
  name        = "${var.project}-alb"
  description = "Public entry point"
  vpc_id      = module.vpc.vpc_id
}

# 80 only redirects to 443 once a domain exists (alb.tf).
resource "aws_vpc_security_group_ingress_rule" "alb_public" {
  for_each = toset(["80", "443"])

  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = tonumber(each.key)
  to_port           = tonumber(each.key)
}

resource "aws_security_group" "service" {
  for_each = local.service_ports

  name        = "${var.project}-${each.key}"
  description = "${each.key}-service tasks"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "service" {
  for_each = local.service_ingress

  security_group_id = aws_security_group.service[each.value.service].id
  referenced_security_group_id = (
    each.value.caller == "alb" ? aws_security_group.alb.id : aws_security_group.service[each.value.caller].id
  )
  ip_protocol = "tcp"
  from_port   = local.service_ports[each.value.service]
  to_port     = local.service_ports[each.value.service]
}

# The ALB health-checks gateway on the actuator port (alb.tf).
resource "aws_vpc_security_group_ingress_rule" "alb_to_gateway_health" {
  security_group_id            = aws_security_group.service["gateway"].id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 9464
  to_port                      = 9464
}

# The one-off db-init task (ecs.tf): reaches Postgres as the admin to create the databases.
resource "aws_security_group" "db_init" {
  name        = "${var.project}-db-init"
  description = "One-off database setup task"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_security_group" "postgres" {
  name        = "${var.project}-postgres"
  description = "RDS PostgreSQL"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "postgres" {
  for_each = merge(
    { for name in local.postgres_clients : name => aws_security_group.service[name].id },
    { db-init = aws_security_group.db_init.id },
  )

  security_group_id            = aws_security_group.postgres.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_security_group" "redis" {
  name        = "${var.project}-redis"
  description = "ElastiCache Valkey"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "redis" {
  for_each = toset(local.redis_clients)

  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.service[each.key].id
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
}

resource "aws_security_group" "vpc_endpoints" {
  name        = "${var.project}-vpc-endpoints"
  description = "Interface VPC endpoints"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "vpc_endpoints" {
  for_each = merge(
    { for name, sg in aws_security_group.service : name => sg.id },
    { db-init = aws_security_group.db_init.id },
  )

  security_group_id            = aws_security_group.vpc_endpoints.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}

# ponytail: outbound open on every group — inbound rules carry the isolation. Narrow egress to the
# endpoint group + NAT (SES, Discord) if outbound control is ever required (2-13).
resource "aws_vpc_security_group_egress_rule" "all" {
  for_each = merge(
    { for name, sg in aws_security_group.service : name => sg.id },
    { alb = aws_security_group.alb.id, db-init = aws_security_group.db_init.id },
  )

  security_group_id = each.value
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
