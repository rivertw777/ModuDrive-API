.PHONY: service infra \
       gateway member auth

BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[0;33m
RED := \033[0;31m
NC := \033[0m

SERVICE_COMPOSE_FILE := .docker/docker-compose.service.yml
INFRA_COMPOSE_FILE := .docker/docker-compose.infra.yml

service:
	@echo "$(BLUE)🚀 Starting all services...$(NC)"
	@chmod +x .scripts/start.sh
	@./.scripts/start.sh

infra:
	@echo "$(GREEN)🗄️  Starting infrastructure...$(NC)"
	@docker-compose -f $(INFRA_COMPOSE_FILE) up -d

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
