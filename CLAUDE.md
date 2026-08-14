# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ModuDrive is a cloud-drive microservices backend built with **Spring Boot 4.0.0**, **Java 25**, and **Spring Cloud 2025.1.1**, organized as a Gradle multi-module project. Services register with Netflix Eureka for service discovery. Inter-service calls use OpenFeign (`auth-service` → `member-service`) or `WebClient` (`gateway-service` → `auth-service`), with Resilience4j providing circuit breaking and retry.

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

# Start infra (Postgres)
make infra

# Test + build image + start all services
make service

# Test + build image + start a single service
make member   # or: make gateway, make auth, make file, make storage, make notification
```

Docker Compose files are at `.docker/docker-compose.service.yml` (services) and `.docker/docker-compose.infra.yml` (Postgres, MinIO). The shared `Dockerfile` lives at `.docker/Dockerfile`, referenced by every service's `build.gradle` via its `docker` task.

The active Spring profile (`dev`) is injected via `SPRING_PROFILES_ACTIVE` in `docker-compose.service.yml`, not hardcoded in `application.yml`.

## Service Port Map

| Service               | Port  | Description                              |
|-----------------------|-------|------------------------------------------|
| eureka-server         | 10000 | Netflix Eureka service registry          |
| gateway-service       | 10001 | Spring Cloud Gateway (WebFlux/reactive)  |
| member-service        | 10010 | User signup, lookup, password validation |
| auth-service          | 10011 | JWT login + token validation             |
| file-service          | 10012 | File metadata, versioning, sharing, directory management |
| storage-service       | 10013 | Block-level file storage — split, compress, encrypt, upload/download via S3/MinIO |
| notification-service  | 10014 | Long-polling notification delivery       |

Swagger UI for all services is aggregated at the gateway: `http://localhost:10001/swagger-ui.html`.

## Architecture: Hexagonal (Ports & Adapters)

Every service follows strict hexagonal architecture (`domain/` → `application/` → `adapter/`). Key annotations from `common:core`: `@UseCase` (service implementations), `@WebAdapter` (REST controllers), `@PersistenceAdapter` (JPA adapters) — all package-private by default; only interfaces are public.

For the full layer breakdown, naming conventions, dependency-direction rules, and the step-by-step workflow for adding a new use case, use the `hexagonal-architecture` skill.

## Common Modules

| Module                              | Purpose                                                      |
|-------------------------------------|--------------------------------------------------------------|
| `common:core`                       | `@UseCase`/`@WebAdapter`/`@PersistenceAdapter`, `ApiResponse<T>`, `BusinessException`, `ExceptionCase` interface, `SelfValidating`, `LoggingAspect` |
| `common:api`                        | Shared DTOs for cross-service calls (auth, member)           |
| `common:infrastructure:jpa`         | `BaseTimeEntity` (JPA auditing), `AuditingConfig`            |
| `common:infrastructure:kafka`       | `spring-boot-starter-kafka` — not yet referenced by any service; add to a service's build.gradle when its first producer/consumer lands |
| `common:infrastructure:redis`       | `spring-boot-starter-data-redis` — used by auth-service for token storage |
| `common:infrastructure:resilience4j`| `CircuitBreakerEventConfig`, `RetryEventConfig`, `FeignFallbackUtils` |
| `common:infrastructure:spring-cloud`| `spring-cloud-starter-netflix-eureka-client`, `spring-cloud-starter-openfeign` — all services that register with Eureka or use Feign depend on this module |
| `common:infrastructure:swagger`     | Aggregated OpenAPI/Swagger UI config (dev profile)           |

Application services (auth, member, gateway) depend on `common:core`, `common:api`, and `common:infrastructure:spring-cloud`. JPA services also depend on `common:infrastructure:jpa`. `eureka-server` is a standalone registry and does not depend on any common module.

Root `build.gradle`'s `subprojects {}` block applies the Spring Boot plugin (and disables `bootJar`/`bootRun`/`bootBuildImage`) to every module — including implicit intermediate directories like `common` and `services`, which Gradle creates automatically from nested `include(...)` paths in `settings.gradle` even without their own `build.gradle` file. Only the 4 runnable services re-enable those tasks in their own `build.gradle`.

## Auth Flow

1. Client sends credentials to `POST /api/v1/auth/login` via the gateway.
2. `auth-service` calls `member-service` via Feign (`POST /api/v1/member/authenticate`) to verify credentials.
3. On success, `auth-service` returns a `TokenPair` (access + refresh JWT).
4. For protected routes, the gateway's `CustomServerSecurityContextRepository` calls `auth-service` (`POST /api/v1/auth/validate-token`) via `WebClient` to validate the Bearer token and inject the `SecurityContext`.

## Error Handling

Each service defines a `<Domain>ExceptionCase` enum implementing `ExceptionCase` (from `common:core`). Throw `BusinessException(exceptionCase)` from domain/service code. `GlobalExceptionHandler` (in `common:core`) translates these to `ApiResponse.error(...)` responses automatically.

## Git Convention

@.github/CONTRIBUTING.md

## Testing

Tests use **JUnit 5** (`useJUnitPlatform()`) with H2 in-memory database for JPA services (no MySQL required for tests). Test heap is capped at 1 GB. Test classes live in `src/test/java` mirroring the main package structure.

For which classes require tests, which test type per layer, the given-when-then/BDDMockito/AssertJ conventions, and the 70% coverage policy, use the `test-writing` skill.
