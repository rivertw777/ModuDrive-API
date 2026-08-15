#!/bin/bash

set -e

INFRA_COMPOSE_FILE=".docker/docker-compose.infra.yml"

echo "➡️ Wiping infrastructure data volumes..."
docker-compose -f "$INFRA_COMPOSE_FILE" down -v

echo "➡️ Restarting..."
./.scripts/start.sh
