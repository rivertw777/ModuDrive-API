#!/bin/bash
# .docs/perf-test/001-upload.md 2 — measures each state through the WEB, one after another, the same day.
#   measure.sh <state>:<web> ...    e.g. measure.sh base:web1 c684:web1 c693:web2 c897:web2
# <state>: file/storage-service images tagged blog-<state> (run.sh image). <web>: the WEB build in $WEB_BUILDS/<web>
# (dist/ built with VITE_API_BASE_URL=http://localhost:10001), served on CLIENT_URL (localhost:3000).
# Run from the repo root with infra + services up, `scenario.sh up` done and `upload-web.mjs files` made.
set -u
cd "$(dirname "$0")/../../../.."
WEB_BUILDS=${WEB_BUILDS:-$PWD/.temp/web-bench}
ROUNDS=${ROUNDS:-3}
U=.scripts/perf-test/001-upload/web/upload-web.mjs
PREVIEW=
serve() {
  [ -n "$PREVIEW" ] && kill "$PREVIEW" 2>/dev/null && wait "$PREVIEW" 2>/dev/null
  if curl -sf -o /dev/null http://localhost:3000/login; then echo "something else serves :3000"; exit 1; fi
  (cd "$WEB_BUILDS/$1" && VITE_API_BASE_URL=http://localhost:10001 exec node_modules/.bin/vite preview --port 3000 --strictPort >/dev/null 2>&1) &
  PREVIEW=$!
  until curl -sf -o /dev/null http://localhost:3000/login; do sleep 1; done
}
run() { echo "-- $* $(date +%T)"; WEB_BUILD=$web node $U "$@" >/dev/null || echo "FAILED: $*"; }
trap '[ -n "$PREVIEW" ] && kill "$PREVIEW" 2>/dev/null; node $U cleanup' EXIT
for pair in "$@"; do
  state=${pair%%:*} web=${pair#*:}
  echo "== $state ($web) $(date +%T)"
  .scripts/perf-test/001-upload/run.sh deploy "blog-$state"
  # The gateway kept its connections and circuit to the replaced containers: after a deploy it refused
  # every upload until its fileServiceCircuitBreaker closed again. Restarted, it starts like it did for the first state.
  since=$(date -u +%FT%TZ)
  docker restart modudrive-service-gateway-service-1 >/dev/null
  until docker logs --since "$since" modudrive-service-gateway-service-1 2>&1 | grep -q 'Started .*Application'; do sleep 2; done
  serve "$web"
  node $U cleanup >/dev/null
  run "blog-warm-$state" 1 small
  for users in 1 10; do
    for scenario in big small mixed; do
      run "blog-$state-$scenario-x$users" "$ROUNDS" "$scenario" "$users"
    done
  done
done
echo "done $(date +%T)"
