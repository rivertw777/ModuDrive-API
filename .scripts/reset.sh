#!/bin/bash

set -e

INFRA_COMPOSE_FILE=".docker/docker-compose.infra.yml"
OBSERVABILITY_COMPOSE_FILE=".docker/docker-compose.observability.yml"

echo "➡️ Wiping all data volumes (infra + observability)..."
docker-compose -f "$OBSERVABILITY_COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || true
docker-compose -f "$INFRA_COMPOSE_FILE" down -v --remove-orphans

echo "➡️ Restarting..."
./.scripts/start.sh
