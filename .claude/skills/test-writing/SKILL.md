---
name: test-writing
description: Testing conventions for ModuDrive services — what must be tested, which test type per layer, given-when-then structure with JUnit 5/BDDMockito/AssertJ/@Nested, ArchUnit dependency-direction tests, Testcontainers for Postgres-specific cases, and the 70% coverage policy (JaCoCo recipe included). Use whenever writing or modifying test code under any services/*/src/test/java module, or right after implementing a domain model, service, controller, or adapter that needs accompanying tests.
---

# Test Writing Conventions (ModuDrive)

Every domain/service/adapter class ships with its test in the same change —
tests are not a follow-up task. Config classes and DTOs are exempt from the
coverage bar (see below), but everything else that carries logic is not
optional.

## Coverage policy: 70% minimum

70% is the bar for the classes that are actually in scope (see table below).
This is a **documented policy today, not yet enforced by the build** — no
JaCoCo gate is wired into `./gradlew build` or `check` yet. The exact recipe
to wire it in later is in [JaCoCo gate](#jacoco-gate-recipe-apply-when-ready)
below; it isn't applied yet because coverage is still uneven across services —
`member-service` and `auth-service` are well covered, but `storage-service`
and `notification-service` are thin (notification is barely started), so
turning the gate on today would fail `./gradlew build` immediately. Until
every service clears the bar, treat 70% as something a reviewer checks for,
not something CI blocks on.

**In scope for 70% (must be tested):**
`domain/**`, `application/service/**`, `adapter/in/web/controller/**`,
`adapter/out/persistence/**`, `adapter/out/security/**`, `adapter/out/client/**`

**Excluded from coverage measurement:**
`adapter/in/web/dto/**` (request/response records — no logic to test),
`config/**` and `adapter/in/web/config/**` (`@Configuration` bean wiring),
`*Application.java` (bootstrap entry point). Feign client interfaces
(`adapter/out/client/*/<Name>Client.java`) are also exempt — they're a raw
HTTP contract with no logic; the `<Name>ClientAdapter` that wraps them is
what needs the test.

## What must have a test, and what kind

| Layer | Test type | Spring context? | Collaborators |
|---|---|---|---|
| `domain/model/*`, `domain/vo/*` | Plain JUnit unit test | No | None — call factory methods / behavior directly |
| `application/service/*Service` | Mockito unit test | No | Mock every injected out `Port` interface |
| `adapter/in/web/controller/*Controller` | `@WebMvcTest` slice | Web layer only | Mock the `UseCase` interface with `@MockitoBean` |
| `adapter/out/persistence/*PersistenceAdapter` | `@DataJpaTest` (H2 by default, Testcontainers when Postgres-specific — see below) | JPA layer only | None — hits a real DB |
| `adapter/out/security/*`, `adapter/out/client/*ClientAdapter` | Mockito unit test | No | Mock the raw collaborator when it's an injected interface (Feign client, `PasswordEncoder`) |
| every service module | ArchUnit test (once per service) | No | Imports the service's own compiled classes |
| `config/*`, `adapter/in/web/dto/*` | none required | — | — |

`application/service` tests are the highest-value tests in this codebase:
because the service only depends on out-port *interfaces*, you get full
business-rule coverage with zero Spring context and millisecond-fast tests.
Write these first.

**Exception: static-API wrappers like JJWT.** "Mock the raw collaborator"
only applies when the collaborator is an injected interface with a real
substitution seam (`PasswordEncoder`, a Feign client). JJWT's `Jwts.builder()`/
`Jwts.parserBuilder()` are static fluent factories with no such seam — mocking
them would require `mockStatic` (not a project dependency) and would only
assert that the adapter calls specific builder methods, not that encode/decode
actually round-trips correctly, which is the entire behavior the adapter
exists to provide. For these, test against the real library end-to-end
(generate a token, then parse it back) as `TokenManagerTest` does — this is
the one `adapter/out/security` case where an integration-style test is
correct, not a gap to fix.

## Tools

Already on the classpath via `spring-boot-starter-test` — no new dependency
needed:

- **JUnit 5** (`org.junit.jupiter.api`)
- **Mockito**, used through **BDDMockito** (`org.mockito.BDDMockito.given`),
  not plain `Mockito.when`. `given(...).willReturn(...)` *is* the Given step —
  it's the standard pairing for given-when-then in the Java ecosystem and
  reads naturally as the first block of the test.
- **AssertJ** (`org.assertj.core.api.Assertions.assertThat`) — use this
  instead of JUnit's `assertEquals`/`assertTrue`. Fluent chains read as the
  Then step and produce far more readable failure output.

- **ArchUnit** — already added to every service's `build.gradle`
  (`testImplementation 'com.tngtech.archunit:archunit-junit5:1.4.2'`) alongside
  each service's `HexagonalArchitectureTest`. Must be `1.4.x`+, not `1.3.0` —
  `1.3.0` bundles an ASM version that cannot parse Java 25 class files (this
  project's `sourceCompatibility`). Symptom: `ClassFileImporter().importPackages(...)`
  silently returns **zero** classes (no exception), so every ArchUnit rule
  fails with "failed to check any classes" instead of an ASM/bytecode error.
  If you ever see that message, suspect the ArchUnit/JDK version mismatch
  first, not the rule itself.
- **Testcontainers** (only for the Postgres-specific persistence tests
  described below) — not yet added to any service; needs
  `testImplementation 'org.testcontainers:junit-jupiter:1.20.4'` and
  `testImplementation 'org.testcontainers:postgresql:1.20.4'` in that
  service's `build.gradle` the first time a Postgres-specific test is written.

**Spring Boot 4.0 test-slice annotations are no longer bundled in
`spring-boot-starter-test`.** `@WebMvcTest`, `@DataJpaTest`, etc. moved to
per-technology artifacts with new package names. Add the ones a service
needs explicitly:

- `@WebMvcTest` → `testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'`,
  import from `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
  (not `org.springframework.boot.test.autoconfigure.web.servlet`, which no
  longer exists).
- `@DataJpaTest` → `testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'`,
  import from `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
  (not `org.springframework.boot.test.autoconfigure.orm.jpa`).
- `@MockitoBean` (replaces the deprecated `@MockBean`) is still in
  `org.springframework.test.context.bean.override.mockito.MockitoBean` — no
  extra dependency needed, it ships with `spring-boot-starter-test`.
- `@DataJpaTest` no longer auto-registers plain `@Component`/custom
  stereotype beans (like `@PersistenceAdapter` classes or a `Component`
  mapper) into the slice context — add `@Import({YourPersistenceAdapter.class,
  YourMapper.class})` on the test class explicitly.

## Given-When-Then structure

Group scenarios per method-under-test with JUnit 5 `@Nested` classes — one
`@Nested` class per Given, `@DisplayName` on the nested class describing that
Given, plain `@Test` methods inside for each When/Then. This is the standard
structure for every service test in this codebase, not just an option:

```java
class SignUpMemberServiceTest {

    @Mock private SignUpMemberPort signUpMemberPort;
    @Mock private EncodePasswordPort encodePasswordPort;
    @Mock private CheckEmailExistsPort checkEmailExistsPort;
    @InjectMocks private SignUpMemberService signUpMemberService;

    private final SignUpMemberCommand command = new SignUpMemberCommand(
            new MemberName("river"), new MemberEmail("river@modudrive.com"),
            new MemberPassword("raw-password"));

    @Nested
    @DisplayName("이메일이 중복되지 않았을 때")
    class WhenEmailIsUnique {

        @Test
        void createsMember() {
            given(checkEmailExistsPort.existsByEmail(command.getMemberEmail())).willReturn(false);
            given(encodePasswordPort.encodePassword(command.getMemberPassword()))
                    .willReturn(new MemberPassword("encoded-password"));

            signUpMemberService.signUpMember(command);

            then(signUpMemberPort).should().createMember(any(Member.class));
        }
    }

    @Nested
    @DisplayName("이메일이 이미 존재할 때")
    class WhenEmailIsDuplicate {

        @Test
        void throwsBusinessException() {
            given(checkEmailExistsPort.existsByEmail(command.getMemberEmail())).willReturn(true);

            Throwable thrown = catchThrowable(() -> signUpMemberService.signUpMember(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.DUPLICATE_EMAIL);
            then(signUpMemberPort).shouldHaveNoInteractions();
        }
    }
}
```

`@ExtendWith(MockitoExtension.class)` on the outer class enables
`@Mock`/`@InjectMocks` for every nested class. `then(mock).should()...` is
`BDDMockito`'s verify-step counterpart to `given(...)` — use it for the Then
block instead of plain `Mockito.verify(...)`, for the same given-when-then
readability reason.

Nested class naming: `When<Condition>`, `@DisplayName` in Korean describing
the Given in business terms (test descriptions are output, not identifiers,
so the English-code rule doesn't apply here — this only concerns
`@DisplayName` strings, not method/class names). Test method naming inside a
nested class: `<expectedResult>()`, in English, since the nested class
already carries the scenario.

## Architecture rule tests (ArchUnit)

Every service gets exactly one `HexagonalArchitectureTest` under
`src/test/java/com/moduDrive/<service>/architecture/`, asserting the
dependency-direction rules the `hexagonal-architecture` skill documents by
hand — this turns "domain never imports adapter" from a code-review
checklist item into a test that fails the build:

```java
package com.moduDrive.member.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.moduDrive.member");

    @Test
    void domainDoesNotDependOnApplicationOrAdapter() {
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..");
        rule.check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapter() {
        ArchRule rule = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..");
        rule.check(classes);
    }
}
```

Add one of these the first time a service gets its first real test class —
it costs one file and catches an entire class of review mistakes for free.

## Persistence adapter tests: H2 by default, Testcontainers for Postgres-specific cases

Default every `@DataJpaTest` to the existing H2 in-memory setup (per
CLAUDE.md) — it's fast and sufficient for ordinary query/mapping tests.
Switch a specific test class to Testcontainers only when the behavior under
test depends on something H2 doesn't faithfully emulate: a `unique = true`
constraint's exact violation behavior, a Postgres-only column type, or a
native query using Postgres-specific SQL. Don't switch the whole suite —
only the test class that actually needs Postgres fidelity:

```java
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberPersistenceAdapterUniqueEmailTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // given/when/then: insert two members with the same email,
    // assert the second save throws DataIntegrityViolationException
}
```

## Test data fixtures

Once a service has more than two or three tests building the same domain
object, extract a `src/test/java/.../fixture/<Entity>TestFixture.java` with
static factory methods and sensible defaults (`aMember()`,
`aMemberWithEmail("x@y.com")`), instead of repeating full `Member.create(...)`
argument lists in every test. This mirrors `AccountTestData`/`ActivityTestData`
in the `wikibook/clean-architecture` (buckpal) reference implementation this
project's hexagonal conventions are already based on — see the
`hexagonal-architecture` skill.

## Checklist: tests for a new use case

1. `domain/model` — unit test any new business method or factory validation
   (once `domain` VOs gain real invariants, per the hexagonal-architecture
   review — test the rejection paths, not just the happy path).
2. `application/service/<Verb><Entity>Service` — Mockito test, structured with
   `@Nested` per Given: one for success, one per `BusinessException` branch.
3. `adapter/in/web/controller/<Verb><Entity>Controller` — `@WebMvcTest`, mock
   the UseCase, assert HTTP status and `ApiResponse` body shape.
4. `adapter/out/persistence` — extend/add `@DataJpaTest` cases for any new
   query method on the `SpringData<Entity>Repository`; switch to Testcontainers
   only if the new behavior is Postgres-specific.
5. New `adapter/out/security` or `adapter/out/client` logic — Mockito test
   mocking the raw collaborator, asserting the translation to/from the
   domain object.
6. New service added to the monorepo — add the `archunit-junit5` dependency
   and a `HexagonalArchitectureTest`; every existing service already has one.
7. Run `./gradlew :services:<service>:test` before opening a PR.

## JaCoCo gate recipe (apply when ready)

Not applied to this repo's `build.gradle` yet — see [Coverage
policy](#coverage-policy-70-minimum) for why. When enough existing services
have tests that turning this on won't immediately break `./gradlew build`,
add to the root `build.gradle`'s `subprojects {}` block:

```gradle
apply plugin: 'jacoco'

jacoco {
    toolVersion = "0.8.13"
}

test {
    finalizedBy jacocoTestReport
}

def coverageExclusions = [
        '**/adapter/in/web/dto/**',
        '**/adapter/in/web/config/**',
        '**/config/**',
        '**/*Application.class',
        '**/adapter/out/client/**/*Client.class'
]

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: coverageExclusions)
        }))
    }
}

jacocoTestCoverageVerification {
    dependsOn jacocoTestReport
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: coverageExclusions)
        }))
    }
    violationRules {
        rule {
            limit {
                counter = 'LINE'
                minimum = 0.70
            }
        }
    }
}

check.dependsOn jacocoTestCoverageVerification
```

This makes `./gradlew build` fail below 70% line coverage on the in-scope
packages, with the same exclusions as the coverage table above.
