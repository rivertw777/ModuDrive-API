# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ModuDrive is a cloud-drive microservices backend built with **Spring Boot 3.3.4**, **Java 17**, and **Spring Cloud 2023.0.3**, organized as a Gradle multi-module project. Services communicate via Netflix Eureka (service discovery) and OpenFeign (inter-service HTTP calls), with Resilience4j providing circuit breaking and retry.

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
make member   # or: make gateway, make auth
```

Docker Compose files are at `.docker/docker-compose.service.yml` (services) and `.docker/docker-compose.infra.yml` (Postgres). The shared `Dockerfile` lives at `.docker/Dockerfile`, referenced by every service's `build.gradle` via its `docker` task.

The active Spring profile (`dev`) is injected via `SPRING_PROFILES_ACTIVE` in `docker-compose.service.yml`, not hardcoded in `application.yml`.

## Service Port Map

| Service               | Port  | Description                              |
|-----------------------|-------|------------------------------------------|
| eureka-server         | 10000 | Netflix Eureka service registry          |
| gateway-service       | 10001 | Spring Cloud Gateway (WebFlux/reactive)  |
| member-service        | 10010 | User signup, lookup, password validation |
| auth-service          | 10011 | JWT login + token validation             |

Swagger UI for all services is aggregated at the gateway: `http://localhost:10001/swagger-ui.html`.

## Architecture: Hexagonal (Ports & Adapters)

Every service follows strict hexagonal architecture. The layer structure inside each service is:

```
domain/
  model/          ← Pure business objects; value objects as inner records
  vo/             ← Standalone value objects (e.g. MemberId)
application/
  port/in/
    usecase/      ← UseCase interfaces (entry-point contracts)
    command/      ← Immutable command objects passed to use cases
  port/out/       ← Output port interfaces (persistence, external calls)
  service/        ← @UseCase-annotated implementations (package-private)
adapter/
  in/web/
    controller/   ← @WebAdapter REST controllers (package-private)
    dto/          ← Request/response DTOs
    mapper/       ← Domain ↔ DTO mapping
  out/
    persistence/  ← @PersistenceAdapter JPA adapters + JpaEntity + Mapper
    <other>/      ← Other outbound adapters (e.g. security, Feign)
common/
  <ServiceName>ExceptionCase.java  ← Service-specific error enum
```

Key annotations from `common:core` (all are Spring `@Component` aliases):
- `@UseCase` — marks application service implementations
- `@WebAdapter` — marks inbound REST controllers
- `@PersistenceAdapter` — marks outbound JPA adapters

Domain models use **inner records** as value objects, e.g. `Member.MemberEmail`, `Member.MemberId`. Commands wrap these value objects. Services and controllers are **package-private** by default; only interfaces are public.

## Common Modules

| Module                              | Purpose                                                      |
|-------------------------------------|--------------------------------------------------------------|
| `common:core`                       | `@UseCase`/`@WebAdapter`/`@PersistenceAdapter`, `ApiResponse<T>`, `BusinessException`, `ExceptionCase` interface, `SelfValidating`, `LoggingAspect`, Eureka client |
| `common:api`                        | Shared Feign DTOs for cross-service calls (auth, member)     |
| `common:infrastructure:jpa`         | `BaseTimeEntity` (JPA auditing), `AuditingConfig`            |
| `common:infrastructure:resilience4j`| `CircuitBreakerEventConfig`, `RetryEventConfig`, `FeignFallbackUtils` |

All services depend on `common:core` and `common:api`. JPA services also depend on `common:infrastructure:jpa`.

Root `build.gradle`'s `subprojects {}` block applies the Spring Boot plugin (and disables `bootJar`/`bootRun`/`bootBuildImage`) to every module — including implicit intermediate directories like `common` and `services`, which Gradle creates automatically from nested `include(...)` paths in `settings.gradle` even without their own `build.gradle` file. Only the 4 runnable services re-enable those tasks in their own `build.gradle`.

## Auth Flow

1. Client sends credentials to `POST /api/v1/auth/login` via the gateway.
2. `auth-service` calls `member-service` via Feign (`POST /api/v1/member/authenticate`) to verify credentials.
3. On success, `auth-service` returns a `TokenPair` (access + refresh JWT).
4. For protected routes, the gateway's `CustomServerSecurityContextRepository` calls `auth-service` (`POST /api/v1/auth/validate`) via reactive Feign to validate the Bearer token and inject the `SecurityContext`.

## Error Handling

Each service defines a `<Domain>ExceptionCase` enum implementing `ExceptionCase` (from `common:core`). Throw `BusinessException(exceptionCase)` from domain/service code. `GlobalExceptionHandler` (in `common:core`) translates these to `ApiResponse.error(...)` responses automatically.

## Git Convention

@.github/CONTRIBUTING.md

## Testing

Tests use **JUnit 5** (`useJUnitPlatform()`) with H2 in-memory database for JPA services (no MySQL required for tests). Test heap is capped at 1 GB. Test classes live in `src/test/java` mirroring the main package structure.

## Answer Logging Rule

Every response must be saved as a Markdown file under `.claude/answer/` in the project root.

- Naming: `NNN_slug.md` (e.g. `001_datasource-proxy-setup.md`) — sequential three-digit number + short kebab-case description
- Content: the full response text in Markdown
- One file per conversation turn (one user question = one answer file)