#!/bin/bash
# .docs/perf-test/004 — slow-query isolation. From the repo root, with infra up, `scenario.sh up` done and the
# image built from the working tree (`scenario.sh deploy <label>`). One image, three settings:
#   run.sh off      [n] — no statement/lock limits, no search bulkhead (as before spec 006 2-5)
#   run.sh timeout  [n] — limits only
#   run.sh bulkhead [n] — limits + search bulkhead (as shipped; SEATS=5 to try another seat count)
# Restarts file-service with that state's settings, then runs isolate.py n times (default 3).
set -eu
DIR=$(cd "$(dirname "$0")" && pwd)
state=$1; rounds=${2:-3}
quiet='"jpa":{"properties":{"hibernate":{"show_sql":false}}}'
no_bulkhead='"resilience4j":{"bulkhead":{"instances":{"fileSearch":{"max-concurrent-calls":1000}}}}'
no_limits='"datasource":{"hikari":{"data-source-properties":{"options":"-c statement_timeout=0 -c lock_timeout=0 -c idle_in_transaction_session_timeout=0"}}}'
case $state in
  off)      json="{\"spring\":{$quiet,$no_limits},$no_bulkhead}" ;;
  timeout)  json="{\"spring\":{$quiet},$no_bulkhead}" ;;
  bulkhead)
    json="{\"spring\":{$quiet}}"
    if [ -n "${SEATS:-}" ]; then
      json="{\"spring\":{$quiet},\"resilience4j\":{\"bulkhead\":{\"instances\":{\"fileSearch\":{\"max-concurrent-calls\":$SEATS}}}}}"
    fi ;;
  *) sed -n 2,7p "$0"; exit 1 ;;
esac
override=$(mktemp --suffix=.yml)
trap 'rm -f "$override"' EXIT
printf 'services:\n  file-service:\n    environment:\n      SPRING_APPLICATION_JSON: %s\n' "'$json'" > "$override"
docker-compose -f .docker/docker-compose.service.yml -f "$override" up -d --force-recreate file-service
until docker logs modudrive-service-file-service-1 2>&1 | grep -q 'Started .*Application'; do sleep 2; done
for r in $(seq 1 "$rounds"); do
  docker exec -e PYTHONPATH=/w bench sh -c "cd /w/004-slow-query && python isolate.py $state${SEATS:-}-$r"
done
