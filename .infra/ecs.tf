# Seven ECS services on Fargate. They find each other through Service Connect under the same names
# and ports as compose (http://member-service:10010 ...), so clients.<service>.url needs no change (aws-migration.md 1-3).
locals {
  db_names     = { member = "member_db", file = "file_db", notification = "notification_db", auth = "auth_db" }
  sqs_clients  = ["member", "auth", "file", "storage", "mail", "notification"]
  domain_based = var.domain_name != null

  # Called by another service → registered in Service Connect. Every service is a client: they all
  # send traces to otel-collector:4318 (monitoring.tf), mail included.
  service_connect_servers = ["member", "auth", "file", "storage", "notification"]

  env = {
    for name, port in local.service_ports : name => merge(
      {
        SERVER_PORT = tostring(port)
        # Not "dev": that profile seeds the test users (db/seed) and opens Swagger.
        SPRING_PROFILES_ACTIVE = "prod"
        JAVA_TOOL_OPTIONS      = "-XX:MaxRAMPercentage=75"
        # Traces go to the app's default, http://otel-collector:4318 — the collector's Service Connect
        # name is the compose one (monitoring.tf).
      },
      contains(keys(local.db_names), name) ? {
        # TLS required (Aurora refuses plain connections: rds.force_ssl).
        SPRING_DATASOURCE_URL      = "jdbc:postgresql://${local.db_address[local.db_instance_of[name]]}:5432/${local.db_names[name]}?sslmode=require"
        SPRING_DATASOURCE_USERNAME = "${name}_service"
      } : {},
      contains(local.redis_clients, name) ? {
        REDIS_HOST        = local.redis_host[name]
        REDIS_PORT        = "6379"
        REDIS_SSL_ENABLED = "true"
      } : {},
      # MemoryDB is cluster mode with ACL users: these two switch the client over (application-redis.yml).
      contains(local.redis_clients, name) && var.redis_engine == "memorydb" ? {
        SPRING_DATA_REDIS_CLUSTER_NODES = "${local.redis_host[name]}:6379"
        SPRING_DATA_REDIS_USERNAME      = aws_memorydb_user.redis[local.redis_cluster_of[name]].user_name
      } : {},
      # Endpoint and keys unset: the SDK falls back to the task role (005 1-2).
      contains(local.sqs_clients, name) ? { SPRING_CLOUD_AWS_REGION_STATIC = var.region } : {},
      lookup({
        gateway = { CLIENT_URL = local.client_url }
        file    = { MODUDRIVE_FILE_DEFAULT_QUOTA_BYTES = "21474836480" }
        storage = {
          STORAGE_S3_BUCKET                  = aws_s3_bucket.storage.bucket
          STORAGE_S3_REGION                  = var.region
          STORAGE_BLOCK_SIZE                 = "4194304"
          STORAGE_MULTIPART_MAX_FILE_SIZE    = "5MB"
          STORAGE_MULTIPART_MAX_REQUEST_SIZE = "9MB"
        }
        mail = { CLIENT_URL = local.client_url, MAIL_FROM = local.mail_from }
      }, name, {}),
    )
  }

  # Env vars filled from SSM at task start (secrets.tf) — the values never sit in the task definition.
  secrets = {
    for name, port in local.service_ports : name => merge(
      contains(keys(local.db_names), name) ? { SPRING_DATASOURCE_PASSWORD = aws_ssm_parameter.db_password[name].arn } : {},
      contains(local.redis_clients, name) ? { REDIS_PASSWORD = aws_ssm_parameter.redis_password[local.redis_cluster_of[name]].arn } : {},
      name == "storage" ? { STORAGE_ENCRYPTION_KEY = aws_ssm_parameter.storage_encryption_key.arn } : {},
    )
  }

  # ponytail: placeholders until a domain exists — CORS/CSRF and mail links point nowhere useful, and
  # login can't work anyway without one (the __Host- cookie needs HTTPS on a shared site, aws-migration.md 1-11).
  client_url = local.domain_based ? "https://app.${var.domain_name}" : "https://app.example.com"
  mail_from  = local.domain_based ? "noreply@${var.domain_name}" : "noreply@example.com"
}

resource "aws_ecs_cluster" "main" {
  name = var.project

  depends_on = [aws_cloudwatch_log_group.container_insights]

  setting {
    name  = "containerInsights"
    value = var.container_insights ? "enabled" : "disabled"
  }
}

# Same reason as the Aurora log groups (rds.tf): declared so the CMK covers it.
resource "aws_cloudwatch_log_group" "container_insights" {
  count = var.container_insights ? 1 : 0

  name              = "/aws/ecs/containerinsights/${var.project}/performance"
  kms_key_id        = local.kms_key_arn
  retention_in_days = 14
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
}

resource "aws_service_discovery_http_namespace" "main" {
  name = var.project
}

resource "aws_cloudwatch_log_group" "service" {
  for_each = local.service_ports

  name       = "/ecs/${var.project}/${each.key}-service"
  kms_key_id = local.kms_key_arn
  # Logs are the bulk of the monitoring bill at this scale — keep them short (aws-migration.md 1-13).
  retention_in_days = 14
}

resource "aws_ecs_task_definition" "service" {
  for_each = local.service_ports

  family                   = "${var.project}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.services[each.key].cpu
  memory                   = var.services[each.key].memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task[each.key].arn

  # Scratch space on the task's ephemeral storage — Fargate has no tmpfs.
  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([{
    name      = "${each.key}-service"
    image     = "${aws_ecr_repository.service[each.key].repository_url}:${var.image_tag}"
    essential = true

    portMappings = [
      { name = "http", containerPort = each.value, protocol = "tcp", appProtocol = "http" },
      # Actuator (health, prometheus) — the ALB health check and, later, the ADOT collector.
      { name = "management", containerPort = 9464, protocol = "tcp" },
    ]

    environment = [for k, v in local.env[each.key] : { name = k, value = v }]
    secrets     = [for k, arn in local.secrets[each.key] : { name = k, valueFrom = arn }]

    # The collector's ecs_observer scrapes every container carrying these (monitoring.tf).
    dockerLabels = {
      ECS_PROMETHEUS_EXPORTER_PORT = "9464"
      ECS_PROMETHEUS_METRICS_PATH  = "/actuator/prometheus"
    }

    # Least privilege inside the container (the image already runs as a non-root user): the root
    # filesystem is read-only, so a compromised process can't plant or change files, with only /tmp
    # writable (Tomcat's multipart uploads, the JVM) on the task's own ephemeral storage; and every
    # Linux capability is dropped — a Spring service needs none.
    readonlyRootFilesystem = true
    mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
    linuxParameters = {
      capabilities = { drop = ["ALL"] }
    }

    # A task that's up but no longer answering gets replaced. busybox wget ships in the alpine image.
    healthCheck = {
      command  = ["CMD-SHELL", "wget -qO- http://localhost:9464/actuator/health || exit 1"]
      interval = 30
      timeout  = 5
      retries  = 3
      # The most ECS allows: on demo's quarter vCPU, Spring Boot can take a couple of minutes to start.
      startPeriod = 300
    }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service[each.key].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "ecs"
      }
    }
  }])
}

resource "aws_ecs_service" "service" {
  for_each = local.service_ports

  name            = "${each.key}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  desired_count   = var.services[each.key].min

  capacity_provider_strategy {
    capacity_provider = var.fargate_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 1
  }

  network_configuration {
    subnets          = local.task_subnets
    security_groups  = [aws_security_group.service[each.key].id]
    assign_public_ip = local.task_assign_public_ip
  }

  # A deploy whose tasks never turn healthy rolls itself back to the last good task definition.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Spring Boot takes a while to start — don't let the ALB kill gateway tasks before they're up.
  health_check_grace_period_seconds = each.key == "gateway" ? 300 : null

  dynamic "load_balancer" {
    for_each = each.key == "gateway" ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.gateway.arn
      container_name   = "gateway-service"
      container_port   = each.value
    }
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn

    # Servers answer at <name>-service:<port>, exactly the URLs in application.yml. gateway is
    # client-only: it calls the others, nobody calls it through Service Connect.
    dynamic "service" {
      for_each = contains(local.service_connect_servers, each.key) ? [1] : []
      content {
        port_name      = "http"
        discovery_name = "${each.key}-service"
        client_alias {
          dns_name = "${each.key}-service"
          port     = each.value
        }

        dynamic "tls" {
          for_each = var.hardened ? [1] : []
          content {
            issuer_cert_authority {
              aws_pca_authority_arn = aws_acmpca_certificate_authority_certificate.service_connect[0].certificate_authority_arn
            }
            role_arn = aws_iam_role.ecs_infrastructure[0].arn
            kms_key  = local.kms_key_arn
          }
        }
      }
    }
  }

  # After the first apply autoscaling owns the count, and the deploy workflow owns which revision runs
  # (.github/workflows/deploy.yml) — an apply never rolls a service back to Terraform's image_tag.
  lifecycle {
    ignore_changes = [desired_count, task_definition]
  }

  # A new task pulls its image through the NAT, so the instance route must exist first (demo).
  depends_on = [aws_route.private_nat_instance, aws_iam_role_policy_attachment.ecs_infrastructure, aws_iam_role_policy.ecs_infrastructure_kms]
}

resource "aws_appautoscaling_target" "service" {
  for_each = local.service_ports

  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.service[each.key].name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.services[each.key].min
  max_capacity       = var.services[each.key].max
}

resource "aws_appautoscaling_policy" "cpu" {
  for_each = aws_appautoscaling_target.service

  name               = "${each.key}-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = each.value.service_namespace
  resource_id        = each.value.resource_id
  scalable_dimension = each.value.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

# Creates each instance's databases and their logins, once, after RDS exists and before the services
# first start — RDS is private, so it runs inside the VPC as a one-off task per instance (aws-migration.md 1-5), reusing
# the local script with DB_SERVICES naming that instance's. The commands are the db_init_run_tasks
# output. A second run fails (CREATE ROLE of an existing role) — by design, it only ever runs once.
resource "aws_cloudwatch_log_group" "db_init" {
  name              = "/ecs/${var.project}/db-init"
  retention_in_days = 14
  kms_key_id        = local.kms_key_arn
}

moved {
  from = aws_ecs_task_definition.db_init
  to   = aws_ecs_task_definition.db_init["file"]
}

resource "aws_ecs_task_definition" "db_init" {
  for_each = var.db_instances

  family                   = each.key == "file" ? "${var.project}-db-init" : "${var.project}-db-init-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution.arn

  container_definitions = jsonencode([{
    name      = "db-init"
    image     = "postgres:18-alpine"
    essential = true
    command   = ["sh", "-c", file("${path.module}/../.docker/postgres/postgres_init.sh")]

    environment = [
      { name = "PGHOST", value = local.db_address[each.key] },
      { name = "PGSSLMODE", value = "require" },
      { name = "POSTGRES_USER", value = local.db_master_username },
      { name = "DB_SERVICES", value = join(" ", each.value.clients) },
    ]
    secrets = concat(
      [{ name = "PGPASSWORD", valueFrom = "${local.db_master_secret_arn[each.key]}:password::" }],
      [for client in each.value.clients : { name = local.db_logins[client], valueFrom = aws_ssm_parameter.db_password[client].arn }],
    )

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.db_init.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "ecs"
      }
    }
  }])
}
