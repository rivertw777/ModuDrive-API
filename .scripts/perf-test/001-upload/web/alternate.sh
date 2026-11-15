#!/bin/bash
# .docs/perf-test/001-upload.md 2 — two WEB builds on the same server, alternating every round, so both meet the
# same hour's LocalStack. Each round flips which goes first.
#   alternate.sh <web> <web>        e.g. alternate.sh web2 web3   (env ROUNDS, default 3)
# Run from the repo root with the services deployed (run.sh deploy) and the gateway restarted, as measure.sh does.
# Results: results/ab-<web>-<big|small|mixed>-x<1|10>.jsonl, one line per round, with TIMELINE.
set -u
cd "$(dirname "$0")/../../../.."
WEB_BUILDS=${WEB_BUILDS:-$PWD/.temp/web-bench}
ROUNDS=${ROUNDS:-3}
U=.scripts/perf-test/001-upload/web/upload-web.mjs
A=$1 B=$2
PREVIEW=
serve() {
  if [ -n "$PREVIEW" ]; then kill "$PREVIEW" 2>/dev/null; wait "$PREVIEW" 2>/dev/null; PREVIEW=; fi
  while curl -sf -o /dev/null http://localhost:3000/login; do sleep 1; done
  (cd "$WEB_BUILDS/$1" && VITE_API_BASE_URL=http://localhost:10001 exec node_modules/.bin/vite preview --port 3000 --strictPort >/dev/null 2>&1) &
  PREVIEW=$!
  until curl -sf -o /dev/null http://localhost:3000/login; do sleep 1; done
}
trap '[ -n "$PREVIEW" ] && kill "$PREVIEW" 2>/dev/null; node $U cleanup' EXIT
node $U cleanup
for web in "$A" "$B"; do
  serve "$web"; echo "warm $web $(date +%T)"
  WEB_BUILD=$web node $U "ab-warm-$web" 1 small >/dev/null || echo "FAILED: warm $web"
done
for r in $(seq 1 "$ROUNDS"); do
  if [ $((r % 2)) = 1 ]; then order="$A $B"; else order="$B $A"; fi
  echo "== round $r ($order) $(date +%T)"
  for spec in "big 1" "small 1" "mixed 1" "small 10"; do
    set -- $spec
    for web in $order; do
      serve "$web"; echo "-- $web $1 x$2 $(date +%T)"
      WEB_BUILD=$web TIMELINE=1 node $U "ab-$web-$1-x$2" 1 "$1" "$2" >/dev/null || echo "FAILED: $web $1 x$2"
    done
  done
done
echo "done $(date +%T)"
