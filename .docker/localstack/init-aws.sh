#!/bin/bash
# Local SQS queues, run by LocalStack once it's ready (and on every restart — PERSISTENCE keeps the
# queues, so this has to converge rather than create). Mirror of the AWS queues Terraform creates —
# keep the two in step: every queue is standard, a failed message comes back after the visibility
# timeout, and after maxReceiveCount receives (1 try + 3 retries) it moves to its "-dlq" queue.
# The S3 bucket isn't here: storage-service creates it on startup when pointed at an endpoint.
set -euo pipefail

QUEUES=(
  member-signed-up
  mail-verification-requested
  mail-share-invite-requested
  notification-file-shared
)

for queue in "${QUEUES[@]}"; do
  awslocal sqs create-queue --queue-name "$queue-dlq" >/dev/null
  url=$(awslocal sqs create-queue --queue-name "$queue" --query QueueUrl --output text)
  # Set separately: create-queue refuses an existing queue whose attributes differ from the ones asked for.
  awslocal sqs set-queue-attributes --queue-url "$url" --attributes "{
    \"VisibilityTimeout\": \"10\",
    \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"arn:aws:sqs:${AWS_DEFAULT_REGION}:000000000000:$queue-dlq\\\",\\\"maxReceiveCount\\\":4}\"
  }"
done
