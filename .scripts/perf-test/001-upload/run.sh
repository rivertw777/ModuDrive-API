#!/bin/bash
# .docs/perf-test/001-upload.md — run from the repo root, with infra + services up and `scenario.sh up` done.
#   run.sh image <tag>                     — tag the file/storage-service images built from the working tree as <tag>
#   run.sh deploy <tag>                    — (re)start file-service and storage-service on the images tagged <tag>
#   run.sh <label> <rounds> big <blocks>   — one <blocks> × 4MiB file per round
#   run.sh <label> <rounds> small <files> <bytes>
#   run.sh <label> <rounds> concurrent <users> <kind> <args>...  — <users> drives each uploading <kind> at once
#   run.sh <label> <rounds> mixed                              — one folder of mixed file types (upload.py MIXED)
# Each round is a new drive (or drives); its S3 blocks are deleted after it (LocalStack keeps them in memory).
set -eu
DIR=$(cd "$(dirname "$0")" && pwd)
SERVICES="file-service storage-service"
case $1 in
  image)
    ./gradlew -q :services:file-service:docker :services:storage-service:docker
    for s in $SERVICES; do docker tag "modu-drive-$s:0.0.1-SNAPSHOT" "modu-drive-$s:$2"; done ;;
  deploy)
    for s in $SERVICES; do docker tag "modu-drive-$s:$2" "modu-drive-$s:0.0.1-SNAPSHOT"; done
    docker-compose -f .docker/docker-compose.service.yml up -d --force-recreate $SERVICES
    for s in $SERVICES; do
      until docker logs "modudrive-service-$s-1" 2>&1 | grep -q 'Started .*Application'; do sleep 2; done
    done ;;
  *)
    label=$1 rounds=$2; shift 2
    BUCKET=$(docker exec modudrive-service-storage-service-1 printenv STORAGE_S3_BUCKET)
    for _ in $(seq 1 "$rounds"); do
      line=$(docker exec -e REDIS_PASSWORD="$(docker exec modudrive-infra-redis-1 printenv REDIS_PASSWORD)" \
        -e PYTHONPATH=/w bench sh -c "cd /w/001-upload && python upload.py $label $*")
      echo "$line"
      for user in $(echo "$line" | python3 -c 'import json,sys;print(*json.load(sys.stdin)["users"])'); do
        docker exec modudrive-infra-localstack-1 awslocal s3 rm "s3://$BUCKET/blocks/$user/" --recursive --quiet
      done
    done ;;
esac
