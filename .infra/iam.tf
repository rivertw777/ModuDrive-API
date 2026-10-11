# Two roles per task, as ECS splits them: the execution role is ECS itself pulling the image, writing
# logs and reading secrets before the app starts; the task role is what the app's AWS SDK calls run as.
data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.project}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    actions   = ["ssm:GetParameters"]
    resources = ["arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*"]
  }
  # db-init reads the RDS admin passwords RDS keeps in Secrets Manager.
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = values(local.db_master_secret_arn)
  }
  # Both are encrypted with the customer key when hardened.
  dynamic "statement" {
    for_each = var.hardened ? [1] : []
    content {
      actions   = ["kms:Decrypt"]
      resources = [local.kms_key_arn]
    }
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

# What each service's code touches — least privilege, so a compromised service can't reach another's
# queues (005-messaging-spec 1-2-2). LocalStack checks none of this: a missing permission shows up only
# on AWS, as AccessDenied. Keep in step with the *Queues constants and @SqsListener.
locals {
  queue_producers = {
    member = ["member-signed-up", "mail-verification-requested"]
    auth   = ["mail-login-verification-requested"]
    file   = ["mail-share-invite-requested", "notification-file-shared", "storage-blocks-purge-requested"]
  }
  queue_consumers = {
    file         = ["member-signed-up"]
    mail         = ["mail-verification-requested", "mail-login-verification-requested", "mail-share-invite-requested", "mail-ses-events"]
    notification = ["notification-file-shared"]
    storage      = ["storage-blocks-purge-requested"]
  }
}

resource "aws_iam_role" "task" {
  for_each = local.service_ports

  name               = "${var.project}-${each.key}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

# gateway calls no AWS API, so its role has no policy.
data "aws_iam_policy_document" "task" {
  for_each = { for name, port in local.service_ports : name => port if name != "gateway" }

  # Outbox relay → queue.
  dynamic "statement" {
    for_each = contains(keys(local.queue_producers), each.key) ? [1] : []
    content {
      actions   = ["sqs:SendMessage", "sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
      resources = [for q in local.queue_producers[each.key] : aws_sqs_queue.queue[q].arn]
    }
  }

  # @SqsListener: receive, delete on success, back off by changing visibility, read the redrive policy.
  dynamic "statement" {
    for_each = contains(keys(local.queue_consumers), each.key) ? [1] : []
    content {
      actions = [
        "sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility",
        "sqs:GetQueueUrl", "sqs:GetQueueAttributes",
      ]
      resources = [for q in local.queue_consumers[each.key] : aws_sqs_queue.queue[q].arn]
    }
  }

  # The error handler moves a permanent failure to the DLQ itself; DeadLetterQueueMetrics counts it.
  dynamic "statement" {
    for_each = contains(keys(local.queue_consumers), each.key) ? [1] : []
    content {
      actions   = ["sqs:SendMessage", "sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
      resources = [for q in local.queue_consumers[each.key] : aws_sqs_queue.dlq[q].arn]
    }
  }

  # Blocks: put on upload, get on download, delete on purge. No bucket-level rights (no CreateBucket, 003-aws-migration.md 1-8).
  dynamic "statement" {
    for_each = each.key == "storage" ? [1] : []
    content {
      actions   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
      resources = ["${aws_s3_bucket.storage.arn}/*"]
    }
  }

  # Queues and blocks are encrypted with the customer key when hardened: sending and moving to a DLQ
  # needs a data key, receiving and reading need to decrypt.
  dynamic "statement" {
    for_each = var.hardened && (contains(keys(local.queue_producers), each.key) || contains(keys(local.queue_consumers), each.key) || each.key == "storage") ? [1] : []
    content {
      actions   = ["kms:GenerateDataKey", "kms:Decrypt"]
      resources = [local.kms_key_arn]
    }
  }

  # SendRawEmail checks both the sending identity and the configuration set the mail names.
  dynamic "statement" {
    for_each = each.key == "mail" ? [1] : []
    content {
      actions = ["ses:SendRawEmail"]
      resources = [
        "arn:aws:ses:${var.region}:${data.aws_caller_identity.current.account_id}:identity/*",
        "arn:aws:ses:${var.region}:${data.aws_caller_identity.current.account_id}:configuration-set/${aws_sesv2_configuration_set.mail_events.configuration_set_name}",
      ]
    }
  }

}

resource "aws_iam_role_policy" "task" {
  for_each = data.aws_iam_policy_document.task

  role   = aws_iam_role.task[each.key].id
  policy = each.value.json
}
