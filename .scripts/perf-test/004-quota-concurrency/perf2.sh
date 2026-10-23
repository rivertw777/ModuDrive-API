#!/bin/bash
# .docs/perf-test/004-quota-concurrency.md
# perf2.sh <label> <users-file> — 3 runs x 300 samples for the smallest and largest bulk namespaces
USERS=$(realpath "$2"); cd "$(dirname "$0")"
for run in 1 2 3; do (head -1 "$USERS"; tail -1 "$USERS") | while read N U; do
  L=$(docker run --rm --network modudrive_network -v "$PWD":/w python:3.12-alpine python /w/latency.py $U 300)
  G=$(docker run --rm --network modudrive_network -v "$PWD":/w python:3.12-alpine python /w/usage.py $U 300)
  echo "$1 run=$run files=$N commit:$L usage:$G"
done; done
