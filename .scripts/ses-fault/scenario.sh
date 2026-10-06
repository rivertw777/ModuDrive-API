#!/bin/bash
# Mail failure scenarios (.docs/fault-test/001-ses-mail-failure.md). Run from the repo root, with infra + services up.
#   scenario.sh up      — start the fault proxy and route mail-service's SES calls through it
#   scenario.sh down    — remove the proxy and route mail-service straight to LocalStack again
#   scenario.sh <mode> <wait-seconds> [slow-seconds] [restore-after-seconds]
#     mode: pass | unavailable | throttle | rejected | paused | noconfigset | slow | hang
set -u
DIR=$(cd "$(dirname "$0")" && pwd)
SERVICE_COMPOSE=.docker/docker-compose.service.yml
case ${1:-} in
  up)
    docker rm -f ses-fault >/dev/null 2>&1
    docker run -d --name ses-fault --network modudrive_network -p 127.0.0.1:18081:8081 \
      -v "$DIR:/app:ro" python:3.13-alpine python -u /app/ses_fault.py >/dev/null
    SES_ENDPOINT=http://ses-fault:8080 docker-compose -f $SERVICE_COMPOSE up -d mail-service
    exit ;;
  down)
    docker rm -f ses-fault >/dev/null 2>&1
    SES_ENDPOINT=http://localstack:4566 docker-compose -f $SERVICE_COMPOSE up -d mail-service
    exit ;;
  ""|-h|--help) sed -n 2,6p "$0"; exit 1 ;;
esac
mode=$1; wait=$2; sec=${3:-0}; restore=${4:-}
LS=modudrive-infra-localstack-1
DLQ=http://localhost:4566/000000000000/mail-verification-requested-dlq
inbox() { curl -s localhost:4566/_aws/ses | python3 -c 'import sys,json;print(len(json.load(sys.stdin)["messages"]))'; }
email="$mode-$(date +%s)@example.com"
before=$(inbox); since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
curl -s "localhost:18081/?m=$mode&s=$sec" >/dev/null
start=$(date +%s)
curl -s -o /dev/null -w "API %{http_code} in %{time_total}s\n" -X POST localhost:10001/api/v1/member/verify-email/request \
  -H 'Content-Type: application/json' -H 'Origin: http://localhost:3000' -d "{\"email\":\"$email\"}"
if [ -n "$restore" ]; then sleep "$restore"; curl -s "localhost:18081/?m=pass" >/dev/null; echo "-- SES restored at +${restore}s"; sleep $((wait-restore)); else sleep "$wait"; fi
echo "== mode=$mode  SES calls (proxy):"
docker logs --since "$since" ses-fault 2>&1 | grep 'SES call' | awk -v s=$start '{print "   " $1, $4}'
echo "== mails delivered: $(( $(inbox) - before ))"
echo "== mail-service log:"
docker logs --since "$since" modudrive-service-mail-service-1 2>&1 | grep -E 'WARN|ERROR' | grep -v 'DeadLetterQueue' \
  | sed -E 's/^.*(WARN|ERROR)/\1/' | cut -c1-260 | sort | uniq -c | head -8
echo "== DLQ:"
docker exec $LS awslocal sqs receive-message --queue-url $DLQ --max-number-of-messages 10 --message-attribute-names All \
  --query 'Messages[].MessageAttributes.DeadLetterReason.StringValue' --output text 2>/dev/null | cut -c1-300
docker exec $LS awslocal sqs purge-queue --queue-url $DLQ >/dev/null 2>&1
curl -s "localhost:18081/?m=pass" >/dev/null
