#!/bin/bash
# .docs/perf-test/003-quota-concurrency.md
# run.sh <rounds> <files> <fileBytes> <quotaBytes>
DIR=$(cd "$(dirname "$0")" && pwd)
psql() { docker exec -i modudrive-infra-postgres-1 sh -c 'psql -qtA -U $POSTGRES_USER -d file_db' ; }
for r in $(seq 1 $1); do
  U=$(python3 -c 'import uuid;print(uuid.uuid4())')
  echo "insert into namespace(id,user_id,root_path,quota_bytes,created_at,updated_at,is_deleted) values (gen_random_uuid(),'$U','/$U',$4,now(),now(),false);" | psql
  RES=$(docker run --rm --network modudrive_network -v "$DIR":/w python:3.12-alpine python /w/race.py $U $2 $3)
  USED=$(echo "select coalesce(sum(f.file_size),0) from file f join namespace n on n.id=f.namespace_id where n.user_id='$U' and not f.is_directory and f.status<>'PENDING' and f.deleted_at is null;" | psql)
  CNT=$(echo "select used_bytes from namespace where user_id='$U';" | psql)
  echo "round=$r quota=$4 used=$USED counter=$CNT over=$((USED>$4 ? USED-$4 : 0)) $RES"
done
