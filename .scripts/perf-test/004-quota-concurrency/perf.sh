#!/bin/bash
# .docs/perf-test/004-quota-concurrency.md
# perf.sh <label> <users-file> — commit + usage latency for each bulk namespace
USERS=$(realpath "$2"); cd "$(dirname "$0")"
while read N U; do
  L=$(docker run --rm --network modudrive_network -v "$PWD":/w python:3.12-alpine python /w/latency.py $U 100)
  G=$(docker run --rm --network modudrive_network -v "$PWD":/w python:3.12-alpine python /w/usage.py $U 100)
  echo "$1 files=$N commit:$L usage:$G"
done < "$USERS"
