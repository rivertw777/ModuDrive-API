#!/bin/bash
# Local SQS queues, S3 bucket and SES sender, run by LocalStack every time it's ready. Nothing survives a restart
# (the free plan has no persistence), so this recreates everything — written to converge anyway, in
# case it runs against a LocalStack that kept its state. Mirror of the AWS resources Terraform
# creates — keep the two in step: every queue is standard, a failed message comes back after the
# visibility timeout, and after maxReceiveCount receives (1 try + 3 retries) it moves to its "-dlq".
set -euo pipefail

QUEUES=(
  member-signed-up
  mail-verification-requested
  mail-login-verification-requested
  mail-share-invite-requested
  notification-file-shared
  storage-blocks-purge-requested
  mail-ses-events
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

# Unset in the SQS module's queue test, which runs LocalStack without SES.
if [ -n "${MAIL_FROM:-}" ]; then
  awslocal ses verify-email-identity --email-address "$MAIL_FROM"

  # SES's Send event per mail, tagged with its delivery id, so mail-service can tell a send that timed
  # out but went through from one that didn't. SES → SNS → the mail-ses-events queue, raw (no SNS envelope).
  topic=$(awslocal sns create-topic --name mail-ses-events --query TopicArn --output text)
  awslocal sns subscribe --topic-arn "$topic" --protocol sqs --attributes RawMessageDelivery=true \
    --notification-endpoint "arn:aws:sqs:${AWS_DEFAULT_REGION}:000000000000:mail-ses-events" >/dev/null
  awslocal ses describe-configuration-set --configuration-set-name mail-events >/dev/null 2>&1 || {
    awslocal ses create-configuration-set --configuration-set Name=mail-events
    awslocal ses create-configuration-set-event-destination --configuration-set-name mail-events \
      --event-destination "Name=send-to-sns,Enabled=true,MatchingEventTypes=send,SNSDestination={TopicARN=$topic}"
  }
fi
