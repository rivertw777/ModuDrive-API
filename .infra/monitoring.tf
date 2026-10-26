# Monitoring (002-aws-migration.md 1-13). Storage and query are managed, and the one thing this stack
# runs is the central collector below. Two backends:
#   - Grafana Cloud (var.grafana_cloud, demo): metrics, logs and traces in one Grafana, as local. The
#     alert rules run on its ruler and its alertmanager posts to Discord, as local.
#   - AWS (prod): AMP for metrics, X-Ray for traces, CloudWatch Logs for logs (awslogs, ecs.tf). The
#     alert rules run on AMP's ruler, out through the SNS topic below.
# Either way the AWS-side alarms go to one SNS topic, which a small Lambda posts to Discord. The Watchdog
# alert pings a heartbeat service (healthchecks.io), which alerts on its own when the pings stop.
locals {
  grafana_cloud = var.grafana_cloud != null

  # The rules in monitoring/alert-rules.yaml — same queries and thresholds as
  # .docker/observability/alert-rules.yaml (spec 009), only hints for AWS.
  alert_rules = templatefile("${path.module}/monitoring/alert-rules.yaml", {
    project  = var.project
    services = keys(local.service_ports)
    loki     = local.grafana_cloud
  })
}

# ---------------------------------------------------------------------------------------------------
# Grafana Cloud. The token (access policy with metrics, logs, traces, rules and alerts write) is made
# on grafana.com and put by hand before the first apply:
#   aws ssm put-parameter --name /<project>/GRAFANA_CLOUD_TOKEN --type SecureString --value <token>
data "aws_ssm_parameter" "grafana_cloud_token" {
  count = local.grafana_cloud ? 1 : 0

  name = "/${var.project}/GRAFANA_CLOUD_TOKEN"
}

# Dashboards go through the stack's Grafana API, which takes a Grafana service account token (Admin: setting
# the org's home dashboard needs it), also made by hand:
#   aws ssm put-parameter --name /<project>/GRAFANA_SETUP_TOKEN --type SecureString --value <glsa_…>
data "aws_ssm_parameter" "grafana_setup_token" {
  count = local.grafana_cloud ? 1 : 0

  name = "/${var.project}/GRAFANA_SETUP_TOKEN"
}

data "aws_ssm_parameter" "discord_webhook" {
  for_each = local.grafana_cloud ? aws_ssm_parameter.discord_webhook : {}

  name = each.value.name
}

data "aws_ssm_parameter" "heartbeat_url" {
  count = local.grafana_cloud ? 1 : 0

  name = aws_ssm_parameter.heartbeat_url.name
}

# Rules and alertmanager config go up through the Mimir APIs — the AWS provider has no say there. The
# rule namespace is replaced whole, so a group dropped here is dropped there too.
resource "terraform_data" "grafana_cloud_alerts" {
  count = local.grafana_cloud ? 1 : 0

  triggers_replace = [
    local.alert_rules,
    local.grafana_cloud_alertmanager,
    sha256(data.aws_ssm_parameter.grafana_cloud_token[0].value),
  ]

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    command     = <<-EOT
      set -euo pipefail
      rules="$PROMETHEUS_URL/api/prom/rules/alerts"
      curl -sS --fail-with-body -u "$PROMETHEUS_USER:$TOKEN" -X DELETE "$rules" || true
      for group in $${!RULE_GROUP_*}; do
        curl -sS --fail-with-body -u "$PROMETHEUS_USER:$TOKEN" -H 'Content-Type: application/yaml' \
          --data-binary "$${!group}" "$rules"
      done
      curl -sS --fail-with-body -u "$ALERTMANAGER_USER:$TOKEN" -H 'Content-Type: application/yaml' \
        --data-binary "$ALERTMANAGER_CONFIG" "$ALERTMANAGER_URL/api/v1/alerts"
    EOT
    environment = merge(
      {
        PROMETHEUS_URL      = var.grafana_cloud.prometheus_url
        PROMETHEUS_USER     = var.grafana_cloud.prometheus_user
        ALERTMANAGER_URL    = var.grafana_cloud.alertmanager_url
        ALERTMANAGER_USER   = var.grafana_cloud.alertmanager_user
        ALERTMANAGER_CONFIG = local.grafana_cloud_alertmanager
        TOKEN               = data.aws_ssm_parameter.grafana_cloud_token[0].value
      },
      { for i, g in yamldecode(local.alert_rules).groups : "RULE_GROUP_${i}" => yamlencode(g) },
    )
  }
}

# The local dashboards (.docker/observability/grafana/dashboards/) into the ModuDrive folder. The folder is
# synced whole, as local provisions it: a dashboard whose file is gone is deleted there too.
locals {
  dashboards = { for f in fileset("${path.module}/../.docker/observability/grafana/dashboards", "*.json") :
  f => file("${path.module}/../.docker/observability/grafana/dashboards/${f}") }
}

resource "terraform_data" "grafana_cloud_dashboards" {
  count = local.grafana_cloud ? 1 : 0

  triggers_replace = [
    local.dashboards,
    sha256(data.aws_ssm_parameter.grafana_setup_token[0].value),
  ]

  provisioner "local-exec" {
    interpreter = ["python3", "-c"]
    command     = <<-EOT
      import json, os, urllib.error, urllib.request

      def call(method, path, body=None):
          req = urllib.request.Request(os.environ["GRAFANA_URL"] + path, method=method,
              data=None if body is None else json.dumps(body).encode(),
              headers={"Authorization": "Bearer " + os.environ["TOKEN"], "Content-Type": "application/json"})
          with urllib.request.urlopen(req) as res:
              return json.load(res)

      try:
          call("POST", "/api/folders", {"uid": "modudrive", "title": "ModuDrive"})
      except urllib.error.HTTPError as e:
          if e.code not in (409, 412):  # already there
              raise
      uids = set()
      for name, text in os.environ.items():
          if name.startswith("DASHBOARD_"):
              dashboard = dict(json.loads(text), id=None)
              uids.add(dashboard["uid"])
              call("POST", "/api/dashboards/db", {"dashboard": dashboard, "folderUid": "modudrive", "overwrite": True})
      for hit in call("GET", "/api/search?type=dash-db&folderUIDs=modudrive"):
          if hit["uid"] not in uids:
              call("DELETE", "/api/dashboards/uid/" + hit["uid"])
      call("PUT", "/api/org/preferences", {"homeDashboardUID": "modudrive-overview"})
    EOT
    environment = merge(
      {
        GRAFANA_URL = var.grafana_cloud.grafana_url
        TOKEN       = data.aws_ssm_parameter.grafana_setup_token[0].value
      },
      { for f, text in local.dashboards : "DASHBOARD_${trimsuffix(f, ".json")}" => text },
    )
  }
}

locals {
  # .docker/observability/alertmanager.yml with the webhook URLs inline: a hosted alertmanager reads no files.
  # A channel whose URL isn't in yet goes to service's, as local (spec 009 3).
  discord_webhook = { for k, p in data.aws_ssm_parameter.discord_webhook : k => p.value }
  heartbeat_url   = one(data.aws_ssm_parameter.heartbeat_url[*].value)
  grafana_cloud_alertmanager = local.grafana_cloud ? templatefile("${path.module}/monitoring/alertmanager-grafana-cloud.yaml", {
    heartbeat_url = startswith(local.heartbeat_url, "https://") ? local.heartbeat_url : null
    webhooks = {
      for c in ["messaging", "service", "infra"] : c => (
        startswith(local.discord_webhook["DISCORD_${upper(c)}_WEBHOOK_URL"], "https://")
        ? local.discord_webhook["DISCORD_${upper(c)}_WEBHOOK_URL"]
        : local.discord_webhook["DISCORD_SERVICE_WEBHOOK_URL"]
      )
    }
    discord_template = file("${path.module}/monitoring/discord.tmpl")
  }) : null
}

# ---------------------------------------------------------------------------------------------------
# AWS. Metrics: AMP. No customer key even when hardened — metric labels carry no user data (no userId
# or fileId; 002-aws-migration.md 1-13), and AMP's own key keeps the query side free of KMS grants.
resource "aws_prometheus_workspace" "main" {
  count = local.grafana_cloud ? 0 : 1

  alias = var.project
}

resource "aws_prometheus_rule_group_namespace" "alerts" {
  count = local.grafana_cloud ? 0 : 1

  name         = "alerts"
  workspace_id = aws_prometheus_workspace.main[0].id
  data         = local.alert_rules
}

resource "aws_prometheus_alert_manager_definition" "alerts" {
  count = local.grafana_cloud ? 0 : 1

  workspace_id = aws_prometheus_workspace.main[0].id
  definition = templatefile("${path.module}/monitoring/alertmanager.yaml", {
    topic_arn        = aws_sns_topic.alerts.arn
    region           = var.region
    discord_template = file("${path.module}/monitoring/discord.tmpl")
  })
}

moved {
  from = aws_prometheus_workspace.main
  to   = aws_prometheus_workspace.main[0]
}

moved {
  from = aws_prometheus_rule_group_namespace.alerts
  to   = aws_prometheus_rule_group_namespace.alerts[0]
}

moved {
  from = aws_prometheus_alert_manager_definition.alerts
  to   = aws_prometheus_alert_manager_definition.alerts[0]
}

# ---------------------------------------------------------------------------------------------------
# Alerts → Discord. Everything that can alert publishes here: AMP's alertmanager (without Grafana
# Cloud), the CloudWatch alarms, Budgets and GuardDuty. Not encrypted: CloudWatch and Budgets can't publish to a topic under
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
      INFRA_WEBHOOK_PARAM     = aws_ssm_parameter.discord_webhook["DISCORD_INFRA_WEBHOOK_URL"].name
      ACCOUNT_WEBHOOK_PARAM   = aws_ssm_parameter.discord_webhook["DISCORD_ACCOUNT_WEBHOOK_URL"].name
      HEARTBEAT_URL_PARAM     = aws_ssm_parameter.heartbeat_url.name
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
    resources = concat([for p in aws_ssm_parameter.discord_webhook : p.arn], [aws_ssm_parameter.heartbeat_url.arn])
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

# The forwarder failing (Discord refusing a revoked webhook, say) drops the alert after the async retries,
# and the alarm saying so can't go through it — so it goes by mail alone, and only with alert_email.
resource "aws_sns_topic" "forwarder_failures" {
  count = var.alert_email == null ? 0 : 1

  name = "${var.project}-forwarder-failures"
}

resource "aws_sns_topic_subscription" "forwarder_failures" {
  count = var.alert_email == null ? 0 : 1

  topic_arn = aws_sns_topic.forwarder_failures[0].arn
  protocol  = "email"
  endpoint  = var.alert_email
}

resource "aws_cloudwatch_metric_alarm" "discord_forwarder_errors" {
  count = var.alert_email == null ? 0 : 1

  alarm_name          = "discord-forwarder-errors"
  alarm_description   = "Discord 전달 실패: discord-forwarder Lambda가 실패해 CloudWatch 경보·예산·GuardDuty 알림이 Discord에 안 감 — 로그 확인 (aws logs tail /aws/lambda/${var.project}-discord-forwarder --since 1h), 웹후크가 폐기됐으면 SSM 값 교체"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  dimensions          = { FunctionName = aws_lambda_function.discord_forwarder.function_name }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.forwarder_failures[0].arn]
  ok_actions          = [aws_sns_topic.forwarder_failures[0].arn]
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

# A task running out of room — the container's own numbers, off-heap memory included, which the app's
# metrics can't see. CPU waits out 15 minutes: every JVM start peaks above 100% for a minute or two.
# Memory alarms on one 5-minute peak: past the container limit the task is OOM-killed, not slowed.
resource "aws_cloudwatch_metric_alarm" "service_cpu" {
  for_each = local.service_ports

  alarm_name          = "service-cpu-${each.key}"
  alarm_description   = "서비스 CPU 높음: ${each.key}-service CPU가 15분째 80% 넘음 — 요청량과 느린 trace 확인, 계속되면 services.${each.key}.cpu 상향"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  dimensions          = { ClusterName = aws_ecs_cluster.main.name, ServiceName = aws_ecs_service.service[each.key].name }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "service_memory" {
  for_each = local.service_ports

  alarm_name          = "service-memory-${each.key}"
  alarm_description   = "서비스 메모리 높음: ${each.key}-service 메모리가 90% 넘음, 넘치면 태스크가 OOM으로 재시작 — 그 시각 요청(zip·다운로드 등) 확인, 계속되면 services.${each.key}.memory 상향"
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  dimensions          = { ClusterName = aws_ecs_cluster.main.name, ServiceName = aws_ecs_service.service[each.key].name }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 90
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# RDS and Redis from the inside. Storage needs none: it grows up to max_allocated_storage.
# RDS runs burstable classes in unlimited mode: out of credits it isn't throttled, it bills the surplus.
resource "aws_cloudwatch_metric_alarm" "db_cpu_credit" {
  for_each = { for k, v in aws_db_instance.postgres : k => v if startswith(v.instance_class, "db.t") }

  alarm_name          = "db-cpu-credit-${each.key}"
  alarm_description   = "DB CPU 크레딧 부족: ${each.value.identifier} 크레딧이 30 아래, 바닥나면 넘게 쓴 CPU만큼 추가 요금(무제한 모드) — CPU를 쓰는 쿼리 확인, 계속되면 instance_class 상향"
  namespace           = "AWS/RDS"
  metric_name         = "CPUCreditBalance"
  dimensions          = { DBInstanceIdentifier = each.value.identifier }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 30
  comparison_operator = "LessThanThreshold"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# Under ~64 MB Postgres is swapping. Each pooled connection is a backend process (db_pool_size).
resource "aws_cloudwatch_metric_alarm" "db_memory" {
  for_each = aws_db_instance.postgres

  alarm_name          = "db-memory-${each.key}"
  alarm_description   = "DB 메모리 부족: ${each.value.identifier} 남은 메모리가 10분째 64MB 아래, 스왑으로 느려짐 — DB 연결 수 확인, db_pool_size를 줄이거나 instance_class 상향"
  namespace           = "AWS/RDS"
  metric_name         = "FreeableMemory"
  dimensions          = { DBInstanceIdentifier = each.value.identifier }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 64 * 1024 * 1024
  comparison_operator = "LessThanThreshold"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# Valkey runs noeviction: once full, writes fail — logins, uploads and download quotas with them.
resource "aws_cloudwatch_metric_alarm" "redis_memory" {
  for_each = aws_elasticache_replication_group.redis

  alarm_name          = "redis-memory-${each.key}"
  alarm_description   = "Redis 메모리 높음: ${each.value.id} 사용량이 80% 넘음, 가득 차면 쓰기(로그인·업로드)가 실패 — 큰 키나 남은 업로드 블록 확인, 계속되면 node_type 상향"
  namespace           = "AWS/ElastiCache"
  metric_name         = "DatabaseMemoryUsagePercentage"
  dimensions          = { CacheClusterId = sort(tolist(each.value.member_clusters))[0] }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 80
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

  # What's used before credits: on the free plan credits net the bill to $0, and the account closes
  # once they run out — so this watches the credits burning, not the (zero) bill.
  cost_types {
    include_credit = false
  }

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
# The central collector: one task. Every span of a trace has to reach the same process for tail
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
  dynamic "statement" {
    for_each = local.grafana_cloud ? [] : [1]
    content {
      actions   = ["aps:RemoteWrite"]
      resources = [aws_prometheus_workspace.main[0].arn]
    }
  }
  dynamic "statement" {
    for_each = local.grafana_cloud ? [] : [1]
    content {
      actions   = ["xray:PutTraceSegments", "xray:PutTelemetryRecords", "xray:GetSamplingRules", "xray:GetSamplingTargets"]
      resources = ["*"]
    }
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

  container_definitions = jsonencode([merge({
    name      = "otel-collector"
    essential = true

    # The image's own user has nowhere to write — every directory is root's, and Fargate mounts
    # the scratch volume root-owned 0755. So uid 0, but without a single capability and on a read-only
    # root filesystem: it can write that one volume and nothing else.
    user                   = "0"
    readonlyRootFilesystem = true
    mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
    linuxParameters = {
      capabilities = { drop = ["ALL"] }
    }

    portMappings = [{ name = "otlp", containerPort = 4318, protocol = "tcp", appProtocol = "http" }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.otel_collector.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "ecs"
      }
    }
    }, {
    # The upstream contrib build, as local — it has the Grafana Cloud exporters and auth that ADOT lacks.
    # Distroless, so no container health check: the deployment circuit breaker still catches a crash.
    grafana_cloud = {
      image       = "otel/opentelemetry-collector-contrib:0.159.0"
      command     = ["--config=env:OTEL_CONFIG"]
      environment = [{ name = "OTEL_CONFIG", value = local.otel_collector_config }]
      secrets     = [{ name = "GRAFANA_CLOUD_TOKEN", valueFrom = one(data.aws_ssm_parameter.grafana_cloud_token[*].arn) }]
    }
    aws = {
      image = "public.ecr.aws/aws-observability/aws-otel-collector:v0.49.0"
      # ADOT reads its whole config from this variable.
      environment = [{ name = "AOT_CONFIG_CONTENT", value = local.otel_collector_config }]
      healthCheck = {
        command     = ["/healthcheck"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 30
      }
    }
  }[local.grafana_cloud ? "grafana_cloud" : "aws"])])
}

locals {
  otel_collector_config = templatefile("${path.module}/monitoring/otel-collector.yaml", {
    region          = var.region
    cluster         = aws_ecs_cluster.main.name
    amp_endpoint    = local.grafana_cloud ? null : aws_prometheus_workspace.main[0].prometheus_endpoint
    scrape_interval = var.otel_collector.scrape_interval
    grafana_cloud   = var.grafana_cloud
  })
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

# Created before the old one goes: a description change replaces the group, and the running collector
# holds the old one until its task moves.
resource "aws_security_group" "otel_collector" {
  name_prefix = "${var.project}-otel-collector-"
  description = "Central OTel collector"
  vpc_id      = module.vpc.vpc_id

  lifecycle {
    create_before_destroy = true
  }
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
