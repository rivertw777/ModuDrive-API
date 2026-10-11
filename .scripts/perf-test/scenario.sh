#!/bin/bash
# Query/latency benchmarks for .docs/perf-test/001-002 (scripts in folder-share/). Run from the repo root,
# with infra + services up. 003 (quota/) has its own entry scripts.
#   scenario.sh up                 — pg_stat_statements on (restarts postgres) + bench container on modudrive_network
#   scenario.sh down               — bench container off, pg_stat_statements off (restarts postgres)
#   scenario.sh deploy <label>     — build file-service from the working tree, tag it <label>, (re)start it
#   scenario.sh cascade <label> [sizes...]   — 001: move/trash/restore/purge (env ROUNDS, SKIP_WAIT)
#   scenario.sh contend <label> [sizes...]   — 001: slowest same-drive commit while a cascade runs (env ROUNDS)
#   scenario.sh perm <label>                 — 002: shared-folder permission by depth
#   scenario.sh index <label> [sizes...]     — 001: folder purge in a drive with BACKGROUND other files (env ROUNDS, BACKGROUND)
#   scenario.sh wait               — block until no file-service transaction is still running server-side
#   scenario.sh cleanup            — delete the benchmark's drives and everything under them
set -u
DIR=$(cd "$(dirname "$0")" && pwd)
SERVICE_COMPOSE=.docker/docker-compose.service.yml
PG=modudrive-infra-postgres-1
psql_su() { docker exec -i $PG psql -U modudrive -v ON_ERROR_STOP=1 "$@"; }
pg_restart() { docker restart $PG >/dev/null; until docker exec $PG pg_isready -q -U modudrive -d postgres; do sleep 1; done; }
bench() { docker exec -e ROUNDS="${ROUNDS:-3}" -e SKIP_WAIT="${SKIP_WAIT:-}" -e OPS="${OPS:-trash,purge}" -e BACKGROUND="${BACKGROUND:-1000000}" bench sh -c "cd /w && python $*"; }
case ${1:-} in
  up)
    psql_su -d postgres < "$DIR/sql/01-pg-stat-statements-on.sql" && pg_restart
    psql_su -d file_db -c 'create extension if not exists pg_stat_statements' >/dev/null
    docker rm -f bench >/dev/null 2>&1
    docker run -d --name bench --network modudrive_network -v "$DIR/folder-share:/w" python:3.13-alpine sleep infinity >/dev/null
    docker exec bench pip install -q --root-user-action=ignore "psycopg[binary]" ;;
  down)
    docker rm -f bench >/dev/null 2>&1
    psql_su -d file_db -c 'drop extension if exists pg_stat_statements' >/dev/null
    psql_su -d postgres < "$DIR/sql/02-pg-stat-statements-off.sql" && pg_restart ;;
  deploy)
    ./gradlew :services:file-service:docker -q || exit 1
    docker tag modu-drive-file-service:0.0.1-SNAPSHOT "modu-drive-file-service:$2"
    docker-compose -f $SERVICE_COMPOSE up -d --force-recreate file-service
    until docker logs modudrive-service-file-service-1 2>&1 | grep -q 'Started .*Application'; do sleep 2; done ;;
  cascade) shift; bench cascade.py "$@" ;;
  contend) shift; bench contend.py "$@" ;;
  perm)    shift; bench perm.py "$@" ;;
  index)   shift; bench index.py "$@" ;;
  wait)
    until [ "$(psql_su -d file_db -tA < "$DIR/sql/06-long-transactions.sql" | wc -l)" = 0 ]; do sleep 5; done ;;
  cleanup) psql_su -d file_db < "$DIR/sql/05-cleanup-bench-data.sql" ;;
  *) sed -n 2,13p "$0"; exit 1 ;;
esac
