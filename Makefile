.PHONY: service infra observability network reset \
       gateway member auth file storage mail notification

BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[0;33m
RED := \033[0;31m
NC := \033[0m

SERVICE_COMPOSE_FILE := .docker/docker-compose.service.yml
INFRA_COMPOSE_FILE := .docker/docker-compose.infra.yml
OBSERVABILITY_COMPOSE_FILE := .docker/docker-compose.observability.yml

service:
	@echo "$(BLUE)🚀 Starting all services...$(NC)"
	@chmod +x .scripts/start.sh
	@./.scripts/start.sh

network:
	@docker network inspect modudrive_network >/dev/null 2>&1 || docker network create modudrive_network

infra: network
	@echo "$(GREEN)🗄️ Starting infrastructure...$(NC)"
	@docker-compose -f $(INFRA_COMPOSE_FILE) up -d --remove-orphans

observability: network
	@echo "$(GREEN)📊 Starting observability stack...$(NC)"
	@docker-compose -f $(OBSERVABILITY_COMPOSE_FILE) up -d --remove-orphans

reset:
	@echo "$(RED)🧨 Wiping all data volumes (infra + observability) and restarting everything...$(NC)"
	@chmod +x .scripts/reset.sh
	@./.scripts/reset.sh

gateway:
	@echo "$(BLUE)🚀 Starting Gateway Service...$(NC)"
	@./gradlew :services:gateway-service:test
	@./gradlew :services:gateway-service:docker
	@docker-compose -f $(SERVICE_COMPOSE_FILE) up -d gateway-service

member:
	@echo "$(BLUE)🚀 Starting Member Service...$(NC)"
	@./gradlew :services:member-service:test
	@./gradlew :services:member-service:docker
	@docker-compose -f $(SERVICE_COMPOSE_FILE) up -d member-service

auth:
	@echo "$(BLUE)🚀 Starting Auth Service...$(NC)"
	@./gradlew :services:auth-service:test
	@./gradlew :services:auth-service:docker
	@docker-compose -f $(SERVICE_COMPOSE_FILE) up -d auth-service

file:
	@echo "$(BLUE)🚀 Starting File Service...$(NC)"
	@./gradlew :services:file-service:test
	@./gradlew :services:file-service:docker
	@docker-compose -f $(SERVICE_COMPOSE_FILE) up -d file-service

storage:
	@echo "$(BLUE)🚀 Starting Storage Service...$(NC)"
	@./gradlew :services:storage-service:test
	@./gradlew :services:storage-service:docker
	@docker-compose -f $(SERVICE_COMPOSE_FILE) up -d storage-service

mail:
	@echo "$(BLUE)🚀 Starting Mail Service...$(NC)"
	@./gradlew :services:mail-service:test
	@./gradlew :services:mail-service:docker
	@docker-compose -f $(SERVICE_COMPOSE_FILE) up -d mail-service

notification:
	@echo "$(BLUE)🚀 Starting Notification Service...$(NC)"
	@./gradlew :services:notification-service:test
	@./gradlew :services:notification-service:docker
	@docker-compose -f $(SERVICE_COMPOSE_FILE) up -d notification-service
