#!/bin/bash
# S3 failure scenarios (.docs/fault-test/002-s3-failure.md). Run from the repo root, with infra + services up.
#   scenario.sh up      — start the fault proxy and route storage-service's S3 calls and upload URLs through it
#   scenario.sh down    — remove the proxy and route storage-service straight to LocalStack again
#   scenario.sh <path> <mode> [sec] [ops] [after] [restore-after-seconds]
#     path: upload (commit's block lookup, PUTs) | download | purge (SQS block delete; env WAIT, default 60)
#           | put (the WEB's own upload in a real browser — put.mjs; env FILES: 4MB files in the group, default 1;
#                 env PUT_CAP: seconds before the run stops waiting, default 900)
#           env REPEAT: upload/download that many times in a row (default 1) — e.g. enough calls to open a circuit
#           env GAP: seconds between uploads or downloads (default 0) — e.g. to watch a recovery or an alert
#           env CLIENT_TIMEOUT: seconds a commit or download is waited for (default 60)
#     mode: pass | unavailable | denied | slow | hang | cut | corrupt | lost (handled, never answered) | drop (handled, connection closed)
#     ops:  S3 calls that fail, comma-separated: get,head,list,put,delete,bucket | all
#           (default: upload head,list · download get · purge all)
#   scenario.sh flood <previews> [seconds] — S3's GetObject hangs, <previews> file previews at once; meanwhile, does
#                                             the commit's block lookup (HeadObject, not failing) and upload-URL
#                                             signing (no S3) still answer? Each probed every second.
#   env VIA_GATEWAY=1: downloads, and flood's previews and signings, go through the gateway (a session made for
#                      the run), so its route circuits and request metrics are in the path
set -u
DIR=$(cd "$(dirname "$0")" && pwd)
SERVICE_COMPOSE=.docker/docker-compose.service.yml
LS=modudrive-infra-localstack-1
STORAGE=modudrive-service-storage-service-1
restart_storage() {
  STORAGE_S3_ENDPOINT=$1 STORAGE_S3_PUBLIC_ENDPOINT=$2 docker-compose -f $SERVICE_COMPOSE up -d --force-recreate storage-service
  until docker logs $STORAGE 2>&1 | grep -q 'Started .*Application'; do sleep 2; done
}
case ${1:-} in
  up)
    docker rm -f s3-fault >/dev/null 2>&1
    docker run -d --name s3-fault --network modudrive_network -p 127.0.0.1:18082:8081 -p 127.0.0.1:14566:8080 \
      -v "$DIR:/app:ro" python:3.13-alpine python -u /app/s3_fault.py >/dev/null
    restart_storage http://s3-fault:8080 http://localhost:14566
    exit ;;
  down)
    docker rm -f s3-fault >/dev/null 2>&1
    restart_storage http://localstack:4566 http://localhost:4566
    exit ;;
  upload|download|purge|flood|put) ;;
  *) sed -n 2,19p "$0"; exit 1 ;;
esac
if [ "$1" = flood ]; then
  path=flood mode=hang sec=0 ops=get after=0 restore= downloads=$2 seconds=${3:-60}
else
  path=$1 mode=$2 sec=${3:-0} ops=${4:-} after=${5:-0} restore=${6:-}
fi
case $path in upload) ops=${ops:-head,list} ;; put) ops=${ops:-put} ;; download|flood) ops=${ops:-get} ;; purge) ops=${ops:-all} ;; esac
BUCKET=$(docker exec $STORAGE printenv STORAGE_S3_BUCKET)
QUEUE=http://localhost:4566/000000000000/storage-blocks-purge-requested
DLQ=$QUEUE-dlq
fault() { curl -s "localhost:18082/?m=$1&s=$sec&ops=$ops&after=$after" >/dev/null; }
client() { docker exec -e CLIENT_TIMEOUT="${CLIENT_TIMEOUT:-60}" -e GATEWAY_SESSION="${session:-}" \
  -e GATEWAY_ORIGIN="$(docker exec modudrive-service-gateway-service-1 printenv CLIENT_URL)" s3-fault python /app/client.py "$@"; }
field() { python3 -c "import json,sys;print(json.load(sys.stdin)$1)"; }

# A fresh drive each run (scenario.sh never reuses one, so leftovers can't skew a result).
user=$(python3 -c 'import uuid;print(uuid.uuid4())')
docker exec modudrive-infra-postgres-1 psql -U modudrive -d file_db -qc \
  "insert into namespace(id,user_id,root_path,quota_bytes,used_bytes,created_at,updated_at,is_deleted)
   values (gen_random_uuid(),'$user','/$user',1000000000000,0,now(),now(),false)" || exit 1

# file-service's own circuit to storage-service stays (it isn't part of the S3 defense) — a previous
# scenario may have left it open, which would answer 503 without reaching S3 at all. Polling can't wait it
# out: an open circuit only moves on when calls come in.
circuits() {
  docker exec s3-fault python -c "import urllib.request
for svc in ('storage-service', 'file-service', 'gateway-service'):
    for l in urllib.request.urlopen(f'http://{svc}:9464/actuator/prometheus').read().decode().splitlines():
        if l.startswith('resilience4j_circuitbreaker_state') and any(n in l for n in ('s3CircuitBreaker', 's3Lookup', 's3Read', 's3Delete', 'storageServiceCircuitBreaker', 'storageServiceUploadUrlsCircuitBreaker')) and l.endswith(' 1.0'):
            print('  ', svc, l[l.index('{'):])"
}
echo "== circuits before (anything not closed skews the run — wait and rerun):"
circuits

fault pass
if [ "$path" != upload ] && [ "$path" != put ]; then
  file=$(client upload "$user" 3)
  echo "$file" | grep -q '"fileId": "' || { echo "setup upload failed: $file"; exit 1; }
fi
if [ "$path" = purge ]; then
  # Off the upload schedule, as a day-old block would be — otherwise the purge keeps it (PurgeBlocksService).
  pw=$(docker exec modudrive-infra-redis-1 printenv REDIS_PASSWORD)
  for h in $(echo "$file" | field '["hashes"]' | tr -d "[],'"); do
    docker exec modudrive-infra-redis-1 redis-cli -a "$pw" --no-auth-warning zrem "uploaded-blocks:{${h:0:1}}" "$user:$h" >/dev/null
  done
  sleep 1  # decidedAt after the blocks' LastModified (second precision)
fi

if [ "${VIA_GATEWAY:-}" = 1 ]; then
  # A session for this drive's user, as auth-service writes one (RedisSessionStore), so flood's previews and URL
  # signings can go through the gateway — its own route circuits are what's measured.
  session=$(python3 -c 'import secrets;print(secrets.token_urlsafe(32))')
  pw=$(docker exec modudrive-infra-redis-1 printenv REDIS_PASSWORD)
  key=session:$(printf %s "$session" | sha256sum | cut -d' ' -f1)
  docker exec modudrive-infra-redis-1 redis-cli -a "$pw" --no-auth-warning hset "$key" memberId "$user" roles MEMBER \
    createdAt "$(date +%s%3N)" >/dev/null
  docker exec modudrive-infra-redis-1 redis-cli -a "$pw" --no-auth-warning pexpire "$key" 1800000 >/dev/null
  # A gateway just started takes seconds over its first session check and route call; a 3s limit on the check
  # would fail the run's first requests for that alone.
  echo "== warm-up through the gateway (status, ms): $(client warm "$user")"
fi

sleep 1; since=$(date -u +%Y-%m-%dT%H:%M:%SZ)  # the setup's calls stay out of the counts below
fault "$mode"
[ -n "$restore" ] && (sleep "$restore"; curl -s "localhost:18082/?m=pass" >/dev/null; echo "-- S3 restored at +${restore}s ($(date +%T.%3N))") &
echo "== $path mode=$mode sec=$sec ops=$ops after=$after"
case $path in
  upload)   for _ in $(seq "${REPEAT:-1}"); do echo "-- upload at $(date +%T.%3N)"; client upload "$user" 2; sleep "${GAP:-0}"; done ;;
  put)      node "$DIR/put.mjs" "$user" "${FILES:-1}" "${PUT_CAP:-900}" ;;
  download) for _ in $(seq "${REPEAT:-1}"); do
              client download "$user" "$(echo "$file" | field '["fileId"]')" "$(echo "$file" | field '["sha256"]')"
              sleep "${GAP:-0}"
            done ;;
  flood)    client flood "$user" "$(echo "$file" | field '["fileId"]')" "$(echo "$file" | field '["hashes"]' | tr -d "[] '")" \
              "$downloads" "$seconds" ;;
  purge)
    body=$(echo "$file" | python3 -c "import json,sys,datetime;f=json.load(sys.stdin);print(json.dumps({'ownerId':'$user','hashes':f['hashes'],'decidedAt':datetime.datetime.now(datetime.UTC).isoformat()}))")
    docker exec $LS awslocal sqs send-message --queue-url $QUEUE --message-body "$body" >/dev/null
    sleep "${WAIT:-60}"
    echo "== blocks left in S3: $(docker exec $LS awslocal s3 ls "s3://$BUCKET/blocks/$user/" | wc -l) of 3" ;;
esac
wait
echo "== S3 calls (proxy):"
docker logs --since "$since" s3-fault 2>&1 | grep ' S3 ' | awk '{print "   " $1, $3, $5}' | uniq -c | tail -20
echo "== S3 calls open at once (peak): $(docker logs --since "$since" s3-fault 2>&1 | grep -o 'inflight=[0-9]*' | cut -d= -f2 | sort -n | tail -1)"
echo "== circuits:"
circuits
echo "== storage-service log:"
docker logs --since "$since" $STORAGE 2>&1 | grep -E '"(WARN|ERROR)"' | python3 -c '
import json, sys
for line in sys.stdin:
    d = json.loads(line); print(d["log"]["level"], d["message"][:200])' | sort | uniq -c | head -8
if [ "$path" = purge ]; then
  echo "== DLQ:"
  docker exec $LS awslocal sqs receive-message --queue-url $DLQ --max-number-of-messages 10 --message-attribute-names All \
    --query 'Messages[].MessageAttributes.DeadLetterReason.StringValue' --output text 2>/dev/null | cut -c1-300
  docker exec $LS awslocal sqs purge-queue --queue-url $DLQ >/dev/null 2>&1
fi
fault pass
