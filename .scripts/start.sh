#!/bin/bash

set -e

SERVICE_COMPOSE_FILE=".docker/docker-compose.service.yml"
INFRA_COMPOSE_FILE=".docker/docker-compose.infra.yml"

echo "➡️ Ensuring infrastructure is running..."
docker-compose -f "$INFRA_COMPOSE_FILE" up -d

echo "➡️ Stopping existing app containers..."
docker ps -q --filter name=modudrive-service | xargs -r docker rm -f
docker-compose -f "$SERVICE_COMPOSE_FILE" down 2>/dev/null || true

echo "➡️ Running tests..."
./gradlew test

echo "➡️ Building Docker images..."
./gradlew docker

echo "➡️ Starting app services..."
docker-compose -f "$SERVICE_COMPOSE_FILE" up -d

echo "✅ Deployment complete!"
