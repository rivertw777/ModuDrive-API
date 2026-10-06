# Mirror of .docker/localstack/init-aws.sh — keep the two in step. Standard queues; a failed message
# comes back after the visibility timeout and after 4 receives (1 try + 3 retries) moves to "-dlq",
# where the DLQ alert picks it up and a person redrives it (005-messaging-spec).
locals {
  queues = toset([
    "member-signed-up",
    "mail-verification-requested",
    "mail-login-verification-requested",
    "mail-share-invite-requested",
    "notification-file-shared",
    "storage-blocks-purge-requested",
    "mail-ses-events",
  ])
}

resource "aws_sqs_queue" "dlq" {
  for_each = local.queues

  name = "${each.key}-dlq"
  # Max retention: a dead letter waits for a person, and a long weekend shouldn't lose it.
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "queue" {
  for_each = local.queues

  name                       = each.key
  visibility_timeout_seconds = 10
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = 4
  })
}
