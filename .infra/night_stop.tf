# demo sleeps 00:00–08:00 KST (night_stop): tasks scale to zero, then RDS stops; it starts 20 minutes
# before the tasks come back, which open DB connections on start. ALB, ElastiCache and public IPv4
# can't stop and keep billing. The NAT instance stays up: after a stop/start it came back without
# masquerading, so the tasks couldn't reach SSM or ECR, and stopping it saved only ~$0.12 a day.
# The alarms a sleeping stack trips are muted until the services have had time to start: the AWS ones
# here, the Grafana Cloud ones by a mute interval (monitoring/alertmanager-grafana-cloud.yaml).
# A terraform apply during the night puts the services' min/max back and starts their tasks (without a DB).
locals {
  night_timezone = "Asia/Seoul"
  night_sleep    = "cron(0 0 * * ? *)"
  night_stop_at  = "cron(10 0 * * ? *)"
  night_start_at = "cron(40 7 * * ? *)"
  night_wake     = "cron(0 8 * * ? *)"
  night_alarm_on = "cron(20 8 * * ? *)"

  night_tasks = merge(
    { for k, t in aws_appautoscaling_target.service : k => t if var.night_stop },
    { for t in aws_appautoscaling_target.otel_collector : "otel-collector" => t },
  )
  night_capacity = merge(
    { for k, s in var.services : k => { min = s.min, max = s.max } },
    { otel-collector = { min = 1, max = 1 } },
  )

  night_alarms = [aws_cloudwatch_metric_alarm.gateway_unhealthy.alarm_name, aws_cloudwatch_metric_alarm.alb_5xx.alarm_name]
}

resource "aws_appautoscaling_target" "otel_collector" {
  count = var.night_stop ? 1 : 0

  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.otel_collector.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = 1
  max_capacity       = 1
}

resource "aws_appautoscaling_scheduled_action" "sleep" {
  for_each = local.night_tasks

  name               = "${each.key}-sleep"
  service_namespace  = each.value.service_namespace
  resource_id        = each.value.resource_id
  scalable_dimension = each.value.scalable_dimension
  schedule           = local.night_sleep
  timezone           = local.night_timezone

  scalable_target_action {
    min_capacity = 0
    max_capacity = 0
  }
}

resource "aws_appautoscaling_scheduled_action" "wake" {
  for_each = local.night_tasks

  name               = "${each.key}-wake"
  service_namespace  = each.value.service_namespace
  resource_id        = each.value.resource_id
  scalable_dimension = each.value.scalable_dimension
  schedule           = local.night_wake
  timezone           = local.night_timezone

  scalable_target_action {
    min_capacity = local.night_capacity[each.key].min
    max_capacity = local.night_capacity[each.key].max
  }
}

resource "aws_iam_role" "night_stop" {
  count = var.night_stop ? 1 : 0

  name = "${var.project}-night-stop"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "scheduler.amazonaws.com" }
      Condition = { StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id } }
    }]
  })
}

resource "aws_iam_role_policy" "night_stop" {
  count = var.night_stop ? 1 : 0

  role = aws_iam_role.night_stop[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["rds:StopDBInstance", "rds:StartDBInstance"], Resource = [for i in aws_db_instance.postgres : i.arn] },
      {
        Effect = "Allow", Action = ["cloudwatch:DisableAlarmActions", "cloudwatch:EnableAlarmActions"]
        Resource = concat(
          [aws_cloudwatch_metric_alarm.gateway_unhealthy.arn, aws_cloudwatch_metric_alarm.alb_5xx.arn],
          aws_cloudwatch_metric_alarm.api_unreachable[*].arn,
        )
      },
    ]
  })
}

# Everything the scheduler stops and starts, as SDK calls (universal targets).
locals {
  night_calls = {
    for k, v in merge(
      { for k, i in aws_db_instance.postgres : "db-${k}-stop" => { at = local.night_stop_at, api = "rds:stopDBInstance", input = jsonencode({ DbInstanceIdentifier = i.identifier }) } },
      { for k, i in aws_db_instance.postgres : "db-${k}-start" => { at = local.night_start_at, api = "rds:startDBInstance", input = jsonencode({ DbInstanceIdentifier = i.identifier }) } },
      {
        alarms-off = { at = local.night_sleep, api = "cloudwatch:disableAlarmActions", input = jsonencode({ AlarmNames = local.night_alarms }) }
        alarms-on  = { at = local.night_alarm_on, api = "cloudwatch:enableAlarmActions", input = jsonencode({ AlarmNames = local.night_alarms }) }
      },
    ) : k => v if var.night_stop
  }
}

resource "aws_scheduler_schedule" "night" {
  for_each = local.night_calls

  name                         = "${var.project}-night-${each.key}"
  schedule_expression          = each.value.at
  schedule_expression_timezone = local.night_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:${each.value.api}"
    role_arn = aws_iam_role.night_stop[0].arn
    input    = each.value.input
  }
}

# api-unreachable lives in us-east-1 (monitoring.tf), and a universal target calls its own region.
resource "aws_scheduler_schedule" "night_us_east_1" {
  for_each = var.night_stop && local.domain_based ? {
    alarms-off = { at = local.night_sleep, api = "cloudwatch:disableAlarmActions" }
    alarms-on  = { at = local.night_alarm_on, api = "cloudwatch:enableAlarmActions" }
  } : {}
  provider = aws.us_east_1

  name                         = "${var.project}-night-${each.key}"
  schedule_expression          = each.value.at
  schedule_expression_timezone = local.night_timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:${each.value.api}"
    role_arn = aws_iam_role.night_stop[0].arn
    input    = jsonencode({ AlarmNames = [aws_cloudwatch_metric_alarm.api_unreachable[0].alarm_name] })
  }
}
