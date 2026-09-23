#!/bin/bash
# Local SQS queues and S3 bucket, run by LocalStack every time it's ready. Nothing survives a restart
# (the free plan has no persistence), so this recreates everything — written to converge anyway, in
# case it runs against a LocalStack that kept its state. Mirror of the AWS resources Terraform
# creates — keep the two in step: every queue is standard, a failed message comes back after the
# visibility timeout, and after maxReceiveCount receives (1 try + 3 retries) it moves to its "-dlq".
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

# storage-service also creates it on startup, but only then — a LocalStack restarted on its own would
# otherwise leave uploads failing with NoSuchBucket until storage-service restarts too.
# Unset in the SQS module's queue test, which runs LocalStack without S3.
if [ -n "${STORAGE_S3_BUCKET:-}" ]; then
  # Every region but us-east-1 has to be named again as the bucket's LocationConstraint.
  location=()
  if [ "$STORAGE_S3_REGION" != us-east-1 ]; then
    location=(--create-bucket-configuration "LocationConstraint=$STORAGE_S3_REGION")
  fi
  awslocal s3api head-bucket --bucket "$STORAGE_S3_BUCKET" 2>/dev/null \
    || awslocal s3api create-bucket --bucket "$STORAGE_S3_BUCKET" --region "$STORAGE_S3_REGION" "${location[@]}" >/dev/null
fi
