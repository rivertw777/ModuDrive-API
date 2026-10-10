# Monitoring (aws-migration.md 1-13). Storage and query are managed — AMP for metrics, X-Ray for traces,
# CloudWatch Logs for logs (awslogs, ecs.tf) — and the one thing this stack runs is the central ADOT
# collector below. Alerts are the local Prometheus rules on AMP's ruler; they and the AWS-side alarms
# all go to one SNS topic, which a small Lambda posts to Discord.

# ---------------------------------------------------------------------------------------------------
# Metrics: AMP. No customer key even when hardened — metric labels carry no user data (no userId or
# fileId; aws-migration.md 1-13), and AMP's own key keeps the query side free of KMS grants.
resource "aws_prometheus_workspace" "main" {
  alias = var.project
}

# The rules in monitoring/alert-rules.yaml — same queries and thresholds as
# .docker/observability/alert-rules.yaml (spec 007), only hints for AWS.
resource "aws_prometheus_rule_group_namespace" "alerts" {
  name         = "alerts"
  workspace_id = aws_prometheus_workspace.main.id
  data = templatefile("${path.module}/monitoring/alert-rules.yaml", {
    project  = var.project
    services = keys(local.service_ports)
  })
}

resource "aws_prometheus_alert_manager_definition" "alerts" {
  workspace_id = aws_prometheus_workspace.main.id
  definition = templatefile("${path.module}/monitoring/alertmanager.yaml", {
    topic_arn        = aws_sns_topic.alerts.arn
    region           = var.region
    discord_template = file("${path.module}/monitoring/discord.tmpl")
  })
}

# ---------------------------------------------------------------------------------------------------
# Alerts → Discord. Everything that can alert publishes here: AMP's alertmanager, the CloudWatch
# alarms, Budgets and GuardDuty. Not encrypted: CloudWatch and Budgets can't publish to a topic under
# the AWS-managed SNS key, and the messages are alert text, not data.
resource "aws_sns_topic" "alerts" {
  name = "${var.project}-alerts"
}

resource "aws_sns_topic_policy" "alerts" {
  arn    = aws_sns_topic.alerts.arn
  policy = data.aws_iam_policy_document.alerts_topic.json
}

data "aws_iam_policy_document" "alerts_topic" {
  statement {
    actions   = ["sns:Publish", "sns:GetTopicAttributes"]
    resources = [aws_sns_topic.alerts.arn]
    principals {
      type = "Service"
      identifiers = [
        "aps.amazonaws.com", "cloudwatch.amazonaws.com", "budgets.amazonaws.com", "events.amazonaws.com",
      ]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

data "archive_file" "discord_forwarder" {
  type        = "zip"
  source_file = "${path.module}/monitoring/discord_forwarder.py"
  output_path = "${path.module}/.terraform/discord_forwarder.zip"
}

# Outside the VPC: it only needs SSM and Discord, so it doesn't depend on the NAT it may be warning about.
resource "aws_lambda_function" "discord_forwarder" {
  function_name    = "${var.project}-discord-forwarder"
  role             = aws_iam_role.discord_forwarder.arn
  runtime          = "python3.13"
  architectures    = ["arm64"]
  handler          = "discord_forwarder.handler"
  filename         = data.archive_file.discord_forwarder.output_path
  source_code_hash = data.archive_file.discord_forwarder.output_base64sha256
  timeout          = 15

  environment {
    variables = {
      MESSAGING_WEBHOOK_PARAM = aws_ssm_parameter.discord_webhook["DISCORD_MESSAGING_WEBHOOK_URL"].name
      SERVICE_WEBHOOK_PARAM   = aws_ssm_parameter.discord_webhook["DISCORD_SERVICE_WEBHOOK_URL"].name
    }
  }

  depends_on = [aws_cloudwatch_log_group.discord_forwarder]
}

resource "aws_cloudwatch_log_group" "discord_forwarder" {
  name              = "/aws/lambda/${var.project}-discord-forwarder"
  kms_key_id        = local.kms_key_arn
  retention_in_days = 14
}

resource "aws_iam_role" "discord_forwarder" {
  name = "${var.project}-discord-forwarder"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

data "aws_iam_policy_document" "discord_forwarder" {
  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.discord_forwarder.arn}:*"]
  }
  statement {
    actions   = ["ssm:GetParameter"]
    resources = [for p in aws_ssm_parameter.discord_webhook : p.arn]
  }
  dynamic "statement" {
    for_each = var.hardened ? [1] : []
    content {
      actions   = ["kms:Decrypt"]
      resources = [local.kms_key_arn]
    }
  }
}

resource "aws_iam_role_policy" "discord_forwarder" {
  role   = aws_iam_role.discord_forwarder.id
  policy = data.aws_iam_policy_document.discord_forwarder.json
}

resource "aws_lambda_permission" "discord_forwarder" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.discord_forwarder.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.alerts.arn
}

resource "aws_sns_topic_subscription" "discord_forwarder" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.discord_forwarder.arn
}

# ---------------------------------------------------------------------------------------------------
# AWS-side alarms — what the app's own metrics can't see. Names stay ASCII (SNS subjects are built
# from them); the description's "title: details" is what reaches Discord.
locals {
  alarm_actions = [aws_sns_topic.alerts.arn]
}

# A consumer that's slow or stuck: messages wait in the queue without failing, so no DLQ alert fires.
resource "aws_cloudwatch_metric_alarm" "queue_age" {
  for_each = local.queues

  alarm_name          = "queue-age-${each.key}"
  alarm_description   = "큐 처리 지연: ${each.key}의 가장 오래된 메시지가 5분 넘게 대기 중 — 소비하는 서비스의 상태와 로그 확인 (aws logs tail /ecs/${var.project}/<서비스> --since 15m)"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateAgeOfOldestMessage"
  dimensions          = { QueueName = each.key }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 300
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# 502/503/504 the ALB answers itself — gateway down or unreachable, so the app's 5xx rule never sees them.
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "alb-5xx"
  alarm_description   = "ALB 5xx: ALB가 5분에 5xx를 10건 넘게 직접 응답 — gateway 태스크 상태 확인"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_ELB_5XX_Count"
  dimensions          = { LoadBalancer = aws_lb.main.arn_suffix }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "gateway_unhealthy" {
  alarm_name          = "gateway-unhealthy"
  alarm_description   = "gateway 정상 태스크 없음: ALB 헬스 체크를 통과한 gateway 태스크가 0개 — 서비스 전체가 응답하지 않는다"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HealthyHostCount"
  dimensions          = { LoadBalancer = aws_lb.main.arn_suffix, TargetGroup = aws_lb_target_group.gateway.arn_suffix }
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# demo's NAT instance: if it stops, events, mail and new tasks wait. EC2 recovers it on its own; this says it happened.
resource "aws_cloudwatch_metric_alarm" "nat_instance" {
  count = var.nat == "instance" ? 1 : 0

  alarm_name          = "nat-instance-status"
  alarm_description   = "NAT 인스턴스 상태 검사 실패: 바깥으로 나가는 통신(SQS·SES·이미지 받기)이 멈춤 — EC2 자동 복구를 기다리거나 인스턴스 재시작"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed"
  dimensions          = { InstanceId = aws_instance.nat[0].id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# High-severity GuardDuty findings (hardened, audit.tf), as one line of text.
resource "aws_cloudwatch_event_rule" "guardduty" {
  count = var.hardened ? 1 : 0

  name = "${var.project}-guardduty-high"
  event_pattern = jsonencode({
    source        = ["aws.guardduty"]
    "detail-type" = ["GuardDuty Finding"]
    detail        = { severity = [{ numeric = [">=", 7] }] }
  })
}

resource "aws_cloudwatch_event_target" "guardduty" {
  count = var.hardened ? 1 : 0

  rule = aws_cloudwatch_event_rule.guardduty[0].name
  arn  = aws_sns_topic.alerts.arn
  input_transformer {
    input_paths = {
      severity = "$.detail.severity"
      type     = "$.detail.type"
      title    = "$.detail.title"
    }
    input_template = "\"🚨  **GuardDuty 위협 탐지** (심각도 <severity>)\\n\\n**유형**: <type>\\n\\n**내용**: <title>\""
  }
}

# ---------------------------------------------------------------------------------------------------
# Budget — the alert a trial account needs first.
resource "aws_budgets_budget" "monthly" {
  name         = "${var.project}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_sns_topic_arns  = [aws_sns_topic.alerts.arn]
    subscriber_email_addresses = var.alert_email == null ? [] : [var.alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_sns_topic_arns  = [aws_sns_topic.alerts.arn]
    subscriber_email_addresses = var.alert_email == null ? [] : [var.alert_email]
  }

  depends_on = [aws_sns_topic_policy.alerts]
}

# ---------------------------------------------------------------------------------------------------
# The central ADOT collector: one task. Every span of a trace has to reach the same process for tail
# sampling (errors + over 1s + 5%) to work as it does locally — a sidecar per task would scatter them.
# If it's down, telemetry pauses and the services carry on (they export async and drop on failure).
resource "aws_cloudwatch_log_group" "otel_collector" {
  name              = "/ecs/${var.project}/otel-collector"
  kms_key_id        = local.kms_key_arn
  retention_in_days = 14
}

resource "aws_iam_role" "otel_collector" {
  name               = "${var.project}-otel-collector-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "otel_collector" {
  statement {
    actions   = ["aps:RemoteWrite"]
    resources = [aws_prometheus_workspace.main.arn]
  }
  statement {
    actions   = ["xray:PutTraceSegments", "xray:PutTelemetryRecords", "xray:GetSamplingRules", "xray:GetSamplingTargets"]
    resources = ["*"]
  }
  # ecs_observer: finds the tasks to scrape. List/Describe calls don't take resource ARNs here.
  statement {
    actions = [
      "ecs:ListTasks", "ecs:ListServices", "ecs:DescribeTasks", "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition", "ecs:DescribeContainerInstances", "ec2:DescribeInstances",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "otel_collector" {
  role   = aws_iam_role.otel_collector.id
  policy = data.aws_iam_policy_document.otel_collector.json
}

resource "aws_ecs_task_definition" "otel_collector" {
  family                   = "${var.project}-otel-collector"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.otel_collector.cpu
  memory                   = var.otel_collector.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.otel_collector.arn

  # Where ecs_observer writes its target list (otel-collector.yaml).
  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([{
    name      = "otel-collector"
    image     = "public.ecr.aws/aws-observability/aws-otel-collector:v0.49.0"
    essential = true

    # The image's own user (aoc) has nowhere to write — every directory is root's, and Fargate mounts
    # the scratch volume root-owned 0755. So uid 0, but without a single capability and on a read-only
    # root filesystem: it can write that one volume and nothing else.
    user                   = "0"
    readonlyRootFilesystem = true
    mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
    linuxParameters = {
      capabilities = { drop = ["ALL"] }
    }

    portMappings = [{ name = "otlp", containerPort = 4318, protocol = "tcp", appProtocol = "http" }]

    # ADOT reads its whole config from this variable.
    environment = [{
      name = "AOT_CONFIG_CONTENT"
      value = templatefile("${path.module}/monitoring/otel-collector.yaml", {
        region          = var.region
        cluster         = aws_ecs_cluster.main.name
        amp_endpoint    = aws_prometheus_workspace.main.prometheus_endpoint
        scrape_interval = var.otel_collector.scrape_interval
      })
    }]

    healthCheck = {
      command     = ["/healthcheck"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 30
    }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.otel_collector.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "ecs"
      }
    }
  }])
}

resource "aws_ecs_service" "otel_collector" {
  name            = "otel-collector"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.otel_collector.arn
  desired_count   = 1

  capacity_provider_strategy {
    capacity_provider = var.fargate_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 1
  }

  network_configuration {
    subnets          = local.task_subnets
    security_groups  = [aws_security_group.otel_collector.id]
    assign_public_ip = local.task_assign_public_ip
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # otel-collector:4318, the services' default OTLP endpoint (application-observability.yml) — the
  # same name as in compose.
  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn

    service {
      port_name      = "otlp"
      discovery_name = "otel-collector"
      client_alias {
        dns_name = "otel-collector"
        port     = 4318
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

  depends_on = [aws_route.private_nat_instance, aws_iam_role_policy_attachment.ecs_infrastructure, aws_iam_role_policy.ecs_infrastructure_kms]
}

resource "aws_security_group" "otel_collector" {
  name        = "${var.project}-otel-collector"
  description = "Central ADOT collector"
  vpc_id      = module.vpc.vpc_id
}

# Every service sends its spans here…
resource "aws_vpc_security_group_ingress_rule" "otel_collector" {
  for_each = aws_security_group.service

  security_group_id            = aws_security_group.otel_collector.id
  referenced_security_group_id = each.value.id
  ip_protocol                  = "tcp"
  from_port                    = 4318
  to_port                      = 4318
}

# …and the collector scrapes every service's actuator port.
resource "aws_vpc_security_group_ingress_rule" "otel_collector_scrape" {
  for_each = aws_security_group.service

  security_group_id            = each.value.id
  referenced_security_group_id = aws_security_group.otel_collector.id
  ip_protocol                  = "tcp"
  from_port                    = 9464
  to_port                      = 9464
}

# ---------------------------------------------------------------------------------------------------
# Amazon Managed Grafana: dashboards and Explore over AMP, X-Ray and CloudWatch. Logs in through IAM
# Identity Center — enable it in the account and assign users to the workspace after apply.
resource "aws_iam_role" "grafana" {
  count = var.grafana ? 1 : 0

  name = "${var.project}-grafana"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "grafana.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "grafana" {
  for_each = var.grafana ? toset([
    "arn:aws:iam::aws:policy/AmazonPrometheusQueryAccess",
    "arn:aws:iam::aws:policy/AWSXrayReadOnlyAccess",
    "arn:aws:iam::aws:policy/CloudWatchReadOnlyAccess",
  ]) : toset([])

  role       = aws_iam_role.grafana[0].name
  policy_arn = each.value
}

# Log groups are under the customer key when hardened; reading them back needs it.
resource "aws_iam_role_policy" "grafana_kms" {
  count = var.grafana && var.hardened ? 1 : 0

  role = aws_iam_role.grafana[0].id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "kms:Decrypt", Resource = local.kms_key_arn }]
  })
}

resource "aws_grafana_workspace" "main" {
  count = var.grafana ? 1 : 0

  name                     = var.project
  account_access_type      = "CURRENT_ACCOUNT"
  authentication_providers = ["AWS_SSO"]
  permission_type          = "CUSTOMER_MANAGED"
  role_arn                 = aws_iam_role.grafana[0].arn
  data_sources             = ["PROMETHEUS", "XRAY", "CLOUDWATCH"]
}
