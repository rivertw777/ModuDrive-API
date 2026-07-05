---
name: hexagonal-architecture
description: Layer conventions, naming rules, dependency direction, and the step-by-step workflow for adding or changing code in ModuDrive's hexagonal (ports & adapters) services. Use whenever creating or modifying a domain model, command, use case, port, service, controller, or adapter under any services/*/src/main/java module.
---

# Hexagonal Architecture Conventions (ModuDrive)

One class per use case, not fat CRUD services/controllers. Dependency direction is
one-way: `domain` ← `application` ← `adapter`. `domain` never imports `application`
or `adapter`. `application` never imports `adapter`.

## Core principles

1. **Keep the domain model pure.** Classes in `domain/model/` and `domain/vo/`
   never depend on Spring, JPA, Jackson, or any adapter/framework type — no
   `@Entity`, `@Component`, `@RestController`, no framework-specific
   annotations beyond compile-time Lombok. This is why `Member` (domain) and
   `MemberJpaEntity` (persistence adapter) are two separate classes connected
   by `MemberMapper` — never let a JPA/web annotation leak into `domain/`.

2. **Never expose the domain model as the web model.** Controllers accept and
   return DTOs (`adapter/in/web/dto/`), never the domain class directly. Build
   domain value objects/a `Command` from the request DTO inside the
   controller, and convert the domain result to a response DTO via an
   `<Entity>ResponseMapper` before returning it. This keeps the public API
   contract decoupled from internal domain changes.

3. **Split use cases as narrowly as possible, and map at every boundary.**
   One `UseCase` interface = one operation (`SignUpMemberUseCase`,
   `FindMemberUseCase`) — never a wide interface bundling several operations.
   Each layer boundary does its own mapping in both directions: inbound,
   request DTO → domain value objects → `Command`; outbound, `JpaEntity` →
   domain (via `<Entity>Mapper`) and domain → response DTO (via
   `<Entity>ResponseMapper`). A layer only ever maps to/from its immediate
   neighbor, never reaching two layers over.

4. **Restrict access modifiers aggressively.** Only what other layers must
   call is `public`: `port/in/usecase/*`, `port/out/*`, the domain model, and
   `<Domain>ExceptionCase`. Everything else — `service/*Service`,
   `adapter/in/web/controller/*Controller`,
   `adapter/out/persistence/*PersistenceAdapter`, Feign client interfaces —
   is package-private. Spring still wires package-private
   `@Component`/`@UseCase`/`@WebAdapter`/`@PersistenceAdapter` classes via
   component scanning; package-private only blocks other packages/modules
   from reaching in and bypassing the port.

## Layer map

```
config/
  <Concern>Config.java        Root-level, sibling to domain/application/adapter.
                               Composition-root bean wiring not owned by one
                               adapter (security beans, encoders, JWT props).
domain/
  model/<Entity>.java        Pure business object, no framework deps
  vo/<Vo>.java                Standalone value object (only when shared across
                               multiple domain models in the service)
application/
  port/in/command/<Verb><Entity>Command.java   Immutable input, self-validating
  port/in/usecase/<Verb><Entity>UseCase.java   Public interface, entry contract
  port/out/<Capability>Port.java               Public interface, one capability
  service/<Verb><Entity>Service.java           Package-private, @UseCase impl
exception/
  <Domain>ExceptionCase.java  enum implements ExceptionCase (from common:core)
adapter/
  in/web/controller/<Verb><Entity>Controller.java   Package-private, @WebAdapter
  in/web/config/<Concern>Config.java                 Adapter-scoped config (Swagger, etc.)
  in/web/dto/<Verb><Entity>Request.java              Request/response records
  in/web/mapper/<Entity>ResponseMapper.java          Domain -> response DTO
  out/persistence/<Entity>PersistenceAdapter.java    @PersistenceAdapter
  out/persistence/<Entity>JpaEntity.java             JPA entity
  out/persistence/<Entity>Mapper.java                JpaEntity <-> domain
  out/persistence/SpringData<Entity>Repository.java  extends JpaRepository
  out/<other>/...                                    security, client, etc.
```

## Domain model

Public class, private all-args constructor, static factory methods, value
objects as **inner records** by default (`Member.MemberEmail`,
`Member.MemberId`). Only promote a value object to a standalone
`domain/vo/<Vo>.java` record when it's genuinely shared across multiple domain
models in the service (see `auth-service`'s `domain/vo/MemberEmail.java`,
reused by both `MemberAuthData` and `TokenPair`-adjacent flows).

```java
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member {
    private final UUID id;
    private final String email;
    // ...

    public static Member create(MemberName memberName, MemberEmail memberEmail, ...) { ... }
    public static Member withId(MemberId memberId, ...) { ... }

    public record MemberId(UUID idValue) {}
    public record MemberEmail(String emailValue) {}
}
```

## Command (application/port/in/command)

Extends `SelfValidating<T>` (from `common:core`), fields are domain value
objects (never raw primitives), validates itself in the constructor.

```java
@Getter
@EqualsAndHashCode(callSuper = false)
public class SignUpMemberCommand extends SelfValidating<SignUpMemberCommand> {
    @NotNull private final Member.MemberName memberName;
    @NotNull private final Member.MemberEmail memberEmail;

    public SignUpMemberCommand(Member.MemberName memberName, Member.MemberEmail memberEmail) {
        this.memberName = memberName;
        this.memberEmail = memberEmail;
        this.validateSelf();
    }
}
```

## UseCase + Service

UseCase is a public interface with one method. Service is package-private,
annotated `@UseCase` + `@RequiredArgsConstructor`, injects **out port
interfaces** (never concrete adapters), and throws `BusinessException` with a
`<Domain>ExceptionCase` enum value for domain errors. Mutating methods get
`@Transactional`.

```java
@UseCase
@RequiredArgsConstructor
class SignUpMemberService implements SignUpMemberUseCase {
    private final SignUpMemberPort signUpMemberPort;
    private final EncodePasswordPort encodePasswordPort;
    private final CheckEmailExistsPort checkEmailExistsPort;

    @Transactional
    @Override
    public void signUpMember(SignUpMemberCommand command) {
        if (checkEmailExistsPort.existsByEmail(command.getMemberEmail())) {
            throw new BusinessException(MemberExceptionCase.DUPLICATE_EMAIL);
        }
        // ...
    }
}
```

## Out ports

Small, single-capability interfaces — not one wide repository interface. Name
by capability: `SignUpMemberPort`, `FindMemberPort`, `CheckEmailExistsPort`,
`EncodePasswordPort`. A single adapter class may implement several of them for
the same aggregate (see Persistence adapter below).

## Controller

Package-private, `@WebAdapter` + `@RestController` + `@RequiredArgsConstructor`,
injects the **UseCase interface** (never the Service class). Maps the
`@Valid @RequestBody` DTO into domain value objects, builds the Command, calls
the use case, and returns `ApiResponse<T>`.

```java
@WebAdapter
@RestController
@RequiredArgsConstructor
class SignUpMemberController {
    private final SignUpMemberUseCase signUpMemberUseCase;

    @PostMapping("/api/v1/member/sign-up")
    public ApiResponse<Void> signUpMember(@Valid @RequestBody SignUpMemberRequest request) {
        val command = new SignUpMemberCommand(
                new Member.MemberName(request.name()),
                new Member.MemberEmail(request.email()));
        signUpMemberUseCase.signUpMember(command);
        return ApiResponse.success();
    }
}
```

## Persistence adapter

Package-private, `@PersistenceAdapter` + `@RequiredArgsConstructor`. **One
adapter class per aggregate implements all of that aggregate's out ports**
(don't split into one adapter per port). Delegates to a
`SpringData<Entity>Repository` and converts via an `<Entity>Mapper`. Throws
`BusinessException(<Domain>ExceptionCase.X)` on not-found instead of
propagating `Optional`/`null`.

```java
@RequiredArgsConstructor
@PersistenceAdapter
class MemberPersistenceAdapter implements SignUpMemberPort, FindMemberPort, CheckEmailExistsPort {
    private final SpringDataMemberRepository springDataMemberRepository;
    private final MemberMapper memberMapper;
    // one method per port method
}
```

## Non-persistence out adapters

Live under `adapter/out/<concern>/` (`security/`, `client/<name>/`, etc.).

For inter-service HTTP calls via **Feign**, split into two classes: a
package-private `@FeignClient` interface holding only the raw HTTP contract
(using `common:api` DTOs, annotated with `@CircuitBreaker`/`@Retry` and a
fallback calling `FeignFallbackUtils.handleFallback(cause)`), plus a separate
`@Component` `<Name>ClientAdapter` that implements the domain's out port and
translates the raw Feign response into domain objects. Don't let the out port
or the service layer depend on the Feign DTOs directly — only the
`ClientAdapter` should touch them.

```java
@FeignClient(name = "member-service")
interface MemberClient {
    @PostMapping("/api/v1/member/authenticate")
    @CircuitBreaker(name = "memberServiceCircuitBreaker", fallbackMethod = "authenticateMemberFallback")
    @Retry(name = "memberServiceRetry")
    ApiResponse<AuthenticateMemberResponse> authenticateMember(AuthenticateMemberRequest request);

    default ApiResponse<AuthenticateMemberResponse> authenticateMemberFallback(AuthenticateMemberRequest request, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }
}

@Component
@RequiredArgsConstructor
class MemberClientAdapter implements AuthenticateMemberPort {
    private final MemberClient memberClient;
    @Override
    public MemberAuthData authenticateMember(AuthenticateMemberRequest request) {
        val response = memberClient.authenticateMember(request).getData();
        // translate -> domain object, throw BusinessException on invalid state
    }
}
```

For calls made via `WebClient` (e.g. gateway → auth-service), the calling
class plays the same translation role directly — see
`gateway-service`'s `AuthClient` + `CustomServerSecurityContextRepository`.

## Configuration classes

Two places, chosen by scope — modeled on `buckpal`'s split between
`BuckPalConfiguration` (root, wires cross-cutting beans) and adapter-internal
specifics (see the [wikibook/clean-architecture](https://github.com/wikibook/clean-architecture)
reference implementation this convention is based on):

- **`config/<Concern>Config.java`** (root, sibling to `domain/application/adapter`) —
  a bean is used across layers or isn't owned by any single adapter: password
  encoders, JWT signing properties, security beans. Package-private
  `@Configuration` classes; Spring wires them via component scanning like any
  other stereotype.
- **`adapter/in/web/config/<Concern>Config.java`** (or `adapter/out/.../config/`) —
  a bean only configures that one adapter's concern and would disappear if the
  adapter did: Swagger/OpenAPI docs for the REST layer, a Feign client
  customizer for an out adapter. Never put these at the root — they're not
  cross-cutting, they're adapter detail.

Don't dump both kinds into a single flat `adapter/config/` bucket — that's
what erases the distinction and makes every config class look equally
foundational.

## Error handling

Each service has exactly one `<Domain>ExceptionCase` enum in `exception/`
(a name distinct from the `common:*` Gradle modules — this is a per-service
package, not the cross-service `common:core`/`common:api` shared code),
implementing `ExceptionCase` (`getHttpStatus()`, `getMessage()`) from
`common:core`. It's `public` and referenced from both `application/service`
and `adapter/out/persistence`, so it can't live package-private next to a
single service the way `buckpal`'s `ThresholdExceededException` sits next to
`SendMoneyService` — but keep it to exactly this one enum per service, not a
dumping ground for unrelated helpers. Throw `new
BusinessException(<Domain>ExceptionCase.X)` from domain/service code — never
build `ApiResponse.error(...)` manually; `GlobalExceptionHandler` in
`common:core` does that translation.

## Checklist: adding a new use case end to end

1. Add/extend the domain model in `domain/model/` (value objects as inner
   records unless shared — then `domain/vo/`).
2. Add `<Domain>ExceptionCase` entries in `exception/` for any new failure modes.
3. Add `port/in/command/<Verb><Entity>Command.java` extending `SelfValidating`.
4. Add `port/in/usecase/<Verb><Entity>UseCase.java` (public interface).
5. Add the `port/out/<Capability>Port.java` interfaces the use case needs.
6. Implement `service/<Verb><Entity>Service.java` (`@UseCase`, package-private,
   injects out ports, throws `BusinessException`).
7. Implement/extend the adapters for each new out port: JPA
   (`<Entity>PersistenceAdapter`), Feign/`WebClient` client, security, etc.
8. Add `adapter/in/web/controller/<Verb><Entity>Controller.java`
   (`@WebAdapter`, package-private, injects the UseCase interface).
9. Add request/response DTOs in `adapter/in/web/dto/` and, if the response
   needs mapping, an `<Entity>ResponseMapper`.
10. Run `./gradlew :services:<service>:build` to verify compilation and tests.
