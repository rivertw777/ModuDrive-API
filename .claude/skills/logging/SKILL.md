---
name: logging
description: Logging rules for ModuDrive services — when to log at all, which level (ERROR/WARN/INFO/DEBUG), what must never be logged (PII, secrets, 4xx business exceptions), and message format. Use whenever adding or changing a log call (`@Slf4j`, `log.*`, `LoggerFactory`) under any services/* or common/* module, or when writing a catch block, scheduled job, or SQS consumer.
---

# Logging Rules (ModuDrive)

Format, collection and retention (ECS JSON on stdout → Loki locally, CloudWatch Logs on AWS) are in
`.docs/guide/002-observability.md` §3. This skill covers only what to write in code.

## Log only where an exception stops

Most code logs nothing. Exceptions are already logged once, where they end:

| Path | Who logs it |
|------|-------------|
| HTTP request (servlet services) | `GlobalExceptionHandler` — ERROR for unhandled/infra failures, nothing for `BusinessException` |
| Gateway (WebFlux) | `SessionAuthenticationManager` (auth-service unavailable), `FallbackController` (routed call timed out, failed or hit an open circuit) |
| SQS listener | Spring Cloud AWS (`Error processing message`) + `DeadLetteringErrorHandler` when it dead-letters |
| Feign/WebClient call | `CircuitBreakerEventConfig`, `RetryEventConfig` |

So:

- **Don't** log and rethrow — that's a duplicate line for the same failure.
- **Don't** log a `BusinessException` (not found, forbidden, validation). It's a normal 4xx answer, not an incident.
  That includes security rejections (wrong password, login lockout, wrong verification code): the only
  identifier there is the email, which can't be logged, so a line adds nothing over the gateway's
  per-status request metrics.
- **Don't** log in `domain/` — it has no logger. Log in the service or adapter that decides what happens next.
- **Do** log where you catch and *swallow*: scheduled jobs (`PurgeExpiredTrashService`), best-effort side work
  (`RecordFileAccessService`), outbox/purge loops. If nothing above you will see the exception, you log it.

## Levels

| Level | Meaning | Example |
|-------|---------|---------|
| ERROR | A person must look. Lost or stuck work, no automatic recovery | outbox row parked as `FAILED`, purge of a trash root failed |
| WARN | Something went wrong but the system recovered or will retry, or an alert already fires from a metric | Redis down so a quota check was skipped, lock race retried next run, message moved to a DLQ, circuit breaker opened |
| INFO | Rare state changes worth seeing in normal operation | `Pruned {} file version(s) past retention`, circuit breaker closing again |
| DEBUG | Per-request or per-message detail | idempotency skip, retry attempt — never on in production: find the cause from metrics, traces and WARN/ERROR |

- Nothing at INFO or above on every request/message. Request path, status and latency are already in traces and metrics.
- The gateway 5xx alert's `inspect` command tails the service's logs filtered on `ERROR` (`.infra/monitoring/alert-rules.yaml`) — every ERROR line must be worth reading in an incident.
- A failure that repeats per request while a dependency is down (fallback, open circuit) is WARN or DEBUG: the circuit breaker transition already says it once.

## Never log

- **PII**: email, name, phone, IP. Log the id (`memberId`, `fileId`) instead.
- **Secrets**: passwords, session ids, verification codes, share-link keys, presigned URLs, tokens, full query strings.
- **Request/response bodies** or whole event payloads — they carry the above.

`userId`, `traceId`, `spanId` are added to every line automatically (MDC via `UserIdObservationFilter`, tracing).
Don't put them in the message.

## Format

```java
log.warn("Pruning versions of fileId={} lost a lock race; retried next run", fileId.value());
log.error("Failed to purge trash root fileId={} namespaceId={}", root.getId(), root.getNamespaceId(), e);
```

- English, one sentence, no trailing period.
- Context as `{}` placeholders — never string concatenation. Write `key={}` when the sentence alone doesn't say what the value is
  (`fileId={} namespaceId={}`); `Pruned {} file version(s)` needs no key.
- The exception goes **last, as its own argument** (not `e.getMessage()`), so the stack trace is kept.
  Exception: a WARN that repeats on every request while a dependency is down (Redis, member-service, an open
  circuit) logs `e.toString()` instead — the same stack trace per request only floods the logs.
- `@Slf4j` (Lombok) on the class; no hand-written `LoggerFactory`, no logger in a port interface. Each service's
  architecture test checks that every `Logger` field is named `log` and sits in a class, not an interface,
  and keeps `org.slf4j` out of `domain/`.
- No `[Tag]` prefixes — the `logger` field already says where the line came from.
