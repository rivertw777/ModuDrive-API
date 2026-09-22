# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ModuDrive is a cloud-drive microservices backend built with **Spring Boot 4.1.1**, **Java 25**, and **Spring Cloud 2025.1.3**, organized as a Gradle 9.7.1 multi-module project. There is no service registry: services call each other at fixed URLs (`clients.<service>.url` in each `application.yml` — compose DNS locally, ECS Service Connect on AWS). Inter-service calls use OpenFeign (`auth-service` → `member-service`) or `WebClient` (`gateway-service` → `auth-service`), with Resilience4j providing circuit breaking and retry.

## Build & Run Commands

```bash
# Build all service JARs
./gradlew build

# Build a single service JAR
./gradlew :services:member-service:bootJar

# Run all tests
./gradlew test

# Run tests for a single service
./gradlew :services:member-service:test

# Start infra only (Postgres, Redis, ElasticMQ, MinIO)
make infra

# Start the observability stack only (otel-collector, Tempo, Loki, Promtail, Prometheus, Grafana)
make observability

# Wipe every data volume (infra + observability) and restart infra + observability + services
make reset

# Test + build image + start all services (also brings up infra + observability)
make service

# Test + build image + start a single service
make member   # or: make gateway, make auth, make file, make storage, make mail
```

Docker Compose files are at `.docker/docker-compose.service.yml` (services), `.docker/docker-compose.infra.yml` (Postgres, Redis, ElasticMQ — local SQS, MinIO), and `.docker/docker-compose.observability.yml` (Grafana/Tempo/Loki/Prometheus/OTel). All three attach to `modudrive_network` as an **external** network, created by the `network` Make target (a prerequisite of `infra`/`observability`; `start.sh` creates it inline). Postgres holds one database + login per service (`member_db`/`member_service`, `file_db`/`file_service`, `notification_db`/`notification_service`); each login can only connect to its own database. They are created by `.docker/init/01_postgres_init.sh`, which runs only on an empty volume — `make reset` after changing it. Tables come from Flyway, not this script — see [Database Migrations](#database-migrations-flyway). The shared `Dockerfile` lives at `.docker/Dockerfile`, referenced by every service's `build.gradle` via its `docker` task.

The active Spring profile (`dev`) is injected via `SPRING_PROFILES_ACTIVE` in `docker-compose.service.yml`, not hardcoded in `application.yml`.

## Service Port Map

| Service               | Port  | Description                              |
|-----------------------|-------|------------------------------------------|
| gateway-service       | 10001 | Spring Cloud Gateway (WebFlux/reactive)  |
| member-service        | 10010 | User signup, lookup, password validation |
| auth-service          | 10011 | JWT login + token validation             |
| file-service          | 10012 | File metadata, versioning, sharing, directory management |
| storage-service       | 10013 | Block-level file storage — split, compress, encrypt, upload/download via S3/MinIO |
| mail-service           | 10014 | Async mail sending (SQS consumer)        |
| notification-service   | 10015 | In-app notification feed — records file-share events (SQS consumer), list/mark-read API |

Swagger UI for all services is aggregated at the gateway: `http://localhost:10001/swagger-ui.html`.

## Architecture: Hexagonal (Ports & Adapters)

Every service follows strict hexagonal architecture (`domain/` → `application/` → `adapter/`). Key annotations from `common:core`: `@UseCase` (service implementations), `@WebAdapter` (REST controllers), `@PersistenceAdapter` (JPA adapters), `@EventPublisher` (outbox event publishers), `@EventListener` (SQS consumers; class-level, not Spring's method-level `@EventListener`) — all package-private by default; only interfaces are public.

For the full layer breakdown, naming conventions, dependency-direction rules, and the step-by-step workflow for adding a new use case, use the `hexagonal-architecture` skill.

## Common Modules

| Module                              | Purpose                                                      |
|-------------------------------------|--------------------------------------------------------------|
| `common:core`                       | `@UseCase`/`@WebAdapter`/`@PersistenceAdapter`/`@EventPublisher`/`@EventListener`, `ApiResponse<T>`, `BusinessException`, `ExceptionCase` interface, `SelfValidating`, `LoggingAspect`, `AfterCommit` (run an irreversible call once the transaction commits) |
| `common:api`                        | Shared DTOs for cross-service calls (auth, member)           |
| `common:event`                      | Event DTOs + logical queue names (`*Queues`, used as the queue name as-is) for async cross-service messaging: mail (`VerificationMailRequested`, `ShareInviteMailRequested`, `MailQueues` — member/file-service produce, mail-service consumes), notification (`FileSharedNotified`, `NotificationQueues` — file-service produces, notification-service consumes), member (`MemberSignedUp`, `MemberQueues` — member-service produces, file-service consumes) |
| `common:infrastructure:jpa`         | `BaseTimeEntity` (JPA auditing), `AuditingConfig`            |
| `common:infrastructure:messaging`   | Broker-agnostic messaging: transactional outbox (producers call `OutboxEventRecorder.record(queue, key, event)` inside their `@Transactional`; a relay sends `PENDING` rows through the `MessagePublisher` port and marks them `SENT`, kept 7 days then purged hourly; a row the broker rejects becomes `FAILED` with `failed_at`/`failure_reason`), consumer idempotency (`ProcessedEvents`, keyed by queue + deduplication id — `processed_event` table for services with a DB, Redis keys otherwise) and the permanent-failure list. Opt in per service: `modudrive.messaging.outbox.enabled` / `modudrive.messaging.idempotency.enabled`, and only what's switched on maps its table |
| `common:infrastructure:sqs`         | The SQS adapter for `common:infrastructure:messaging`: `SqsMessagePublisher` (sends to the queue by its name; the deduplication id travels as a `DeduplicationId` message attribute, which consumers read for their idempotency check — standard queues, so the broker deduplicates nothing and imposes no ordering), Spring Cloud AWS (`SqsTemplate`, `@SqsListener`) + shared `application-sqs.yml` (observation on, fail on missing queue) + auto-configured listener error handler: permanent failures (`PermanentFailures` — bad values, validation, conversion; constraint violations are deliberately retryable, since a duplicate delivery loses the idempotency claim) go straight to `<queue>-dlq` with a `DeadLetterReason` attribute; everything else backs off (1s/2s/4s) and, on the last receive the queue's `RedrivePolicy` allows (4), is moved to the DLQ with a `Retries exhausted` reason; the redrive policy itself stays as a safety net for messages that never reach the handler; `DeadLetterQueueMetrics` then polls each consumed queue's DLQ every 30s so an alert can say a consumer gave up. Queues/DLQs/redrive are declared in `.docker/elasticmq/elasticmq.conf` locally and Terraform on AWS. Connection via env: locally `SPRING_CLOUD_AWS_SQS_ENDPOINT` → ElasticMQ, on AWS unset (task role) — used by member/file-service (producers) and file/mail/notification-service (consumers) |
| `common:infrastructure:redis`       | `spring-boot-starter-data-redis` — used by auth-service for token storage, member-service for email verification tokens |
| `common:infrastructure:resilience4j`| `CircuitBreakerEventConfig`, `RetryEventConfig`, `FeignFallbackUtils` |
| `common:infrastructure:spring-cloud`| `spring-cloud-starter-openfeign` — all services that use Feign depend on this module |
| `common:infrastructure:swagger`     | Aggregated OpenAPI/Swagger UI config (dev profile)           |

Application services (auth, member, gateway) depend on `common:core`, `common:api`, and `common:infrastructure:spring-cloud`. JPA services also depend on `common:infrastructure:jpa`.

Root `build.gradle`'s `subprojects {}` block applies the Spring Boot plugin (and disables `bootJar`/`bootRun`/`bootBuildImage`) to every module — including implicit intermediate directories like `common` and `services`, which Gradle creates automatically from nested `include(...)` paths in `settings.gradle` even without their own `build.gradle` file. Only the 4 runnable services re-enable those tasks in their own `build.gradle`.

## Auth Flow

1. Client sends credentials to `POST /api/v1/auth/login` via the gateway.
2. `auth-service` calls `member-service` via Feign (`POST /api/v1/member/authenticate`) to verify credentials.
3. On success, `auth-service` returns a `TokenPair` (access + refresh JWT).
4. For protected routes, the gateway's `CustomServerSecurityContextRepository` calls `auth-service` (`POST /api/v1/auth/validate-token`) via `WebClient` to validate the Bearer token and inject the `SecurityContext`.

## Error Handling

Each service defines a `<Domain>ExceptionCase` enum implementing `ExceptionCase` (from `common:core`). Throw `BusinessException(exceptionCase)` from domain/service code. `GlobalExceptionHandler` (in `common:core`) translates these to `ApiResponse.error(...)` responses automatically.

## Database Migrations (Flyway)

JPA services' schema is managed by Flyway (`db/migration`, `ddl-auto: validate`). Read `.docs/db-migration.md` before changing any entity or migration.

## Git Convention

@.github/CONTRIBUTING.md

## Testing

Tests use **JUnit 5** (`useJUnitPlatform()`). JPA services' persistence tests run on real Postgres via Testcontainers (`src/test/resources/config/application.yml` sets a `jdbc:tc:` URL), on the schema Flyway builds, with `ddl-auto: validate` — so Docker must be running for `./gradlew test`. Test heap is capped at 1 GB. Test classes live in `src/test/java` mirroring the main package structure.

For which classes require tests, which test type per layer, the given-when-then/BDDMockito/AssertJ conventions, and the 70% coverage policy, use the `test-writing` skill.
