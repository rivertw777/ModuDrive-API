# 로깅 스펙

이 문서는 서비스 코드에서 **언제, 어떤 레벨로, 무엇을** 로그로 남기는지 정한 문서입니다.

⚠️ 이 문서가 기준입니다. 코드가 이 문서와 다르면 코드를 고치고, 동작을 바꾸려면 이 문서를 먼저 고칩니다.

로그 형식(ECS JSON)과 수집·보관(로컬 Loki, demo Grafana Cloud Loki)은 [008-observability-spec.md 3](008-observability-spec.md#3-로그)에 있다.

---

## 목차

- [1. 로그를 남기는 곳](#1-로그를-남기는-곳)
  - [1-1. 예외가 멈추는 곳에서 한 번](#1-1-예외가-멈추는-곳에서-한-번)
  - [1-2. 남기지 않는 경우](#1-2-남기지-않는-경우)
  - [1-3. 직접 남기는 경우](#1-3-직접-남기는-경우)
- [2. 레벨](#2-레벨)
- [3. 남기면 안 되는 것](#3-남기면-안-되는-것)
- [4. 메시지 작성법](#4-메시지-작성법)
- [5. 테스트로 막는 것](#5-테스트로-막는-것)

---

## 1. 로그를 남기는 곳

### 1-1. 예외가 멈추는 곳에서 한 번

대부분의 코드는 로그를 남기지 않는다. 예외는 마지막으로 멈추는 곳에서 한 번만 남는다.

| 경로 | 로그를 남기는 곳 |
|---|---|
| HTTP 요청 (서블릿 서비스) | `GlobalExceptionHandler` — 처리 못 한 예외·인프라 장애는 ERROR, `BusinessException`은 남기지 않음 |
| 게이트웨이 (WebFlux) | `SessionAuthenticationManager`(auth-service 응답 없음), `FallbackController`(라우트 호출이 시간 초과·실패·서킷 열림) |
| SQS 리스너 | Spring Cloud AWS(`Error processing message`) + DLQ로 옮길 때 `DeadLetteringErrorHandler` |
| Feign·WebClient 호출 | `CircuitBreakerEventConfig`, `RetryEventConfig` ([006-resilience-spec.md 3](006-resilience-spec.md#3-로그)) |

### 1-2. 남기지 않는 경우

- **로그를 남기고 다시 던지지 않는다.** 같은 실패가 두 줄이 된다.
- **`BusinessException`(없음, 권한 없음, 검증 실패)은 남기지 않는다.** 정상적인 4xx 응답이지 장애가 아니다.
  보안 거절(비밀번호 틀림, 로그인 잠금, 인증 코드 틀림)도 마찬가지다. 거기서 쓸 수 있는 식별자는 이메일뿐인데 이메일은 남길 수 없으므로([3장](#3-남기면-안-되는-것)), 게이트웨이의 상태 코드별 요청 지표보다 나은 정보가 없다.
- **`domain/`에서는 남기지 않는다.** 도메인에는 로거가 없다. 다음에 무엇을 할지 정하는 service나 adapter에서 남긴다.

### 1-3. 직접 남기는 경우

예외를 잡아서 **삼키는 곳**에서는 직접 남긴다. 위에서 아무도 그 예외를 보지 못하기 때문이다.

- 스케줄 작업: `PurgeExpiredTrashService`, `PruneFileVersionsService`, `PurgeUncommittedUploadsService`
- 실패해도 요청은 성공시키는 부가 작업: `RecordFileAccessService`, `RedisDownloadQuotaStore`
- outbox 릴레이·정리 루프: `OutboxRelay`, `JpaProcessedEvents`, `QueueDepthMetrics`

---

## 2. 레벨

| 레벨 | 뜻 | 예 |
|---|---|---|
| ERROR | 사람이 봐야 한다. 일이 사라졌거나 멈췄고 저절로 복구되지 않는다 | outbox 행이 `FAILED`로 빠짐, 휴지통 루트 영구 삭제 실패 |
| WARN | 문제가 있었지만 복구됐거나 다시 시도된다. 또는 지표 알림이 이미 울린다 | Redis가 죽어 다운로드 한도 검사를 건너뜀, 락 경쟁에 져서 다음 실행에 다시 함, 메시지를 DLQ로 옮김, 서킷 열림 |
| INFO | 평소에도 볼 만한 드문 상태 변화 | `Pruned {} file version(s) past retention`, 서킷이 다시 닫힘 |
| DEBUG | 요청·메시지마다 생기는 세부 내용 | 멱등성 검사로 건너뜀, 재시도 한 번 |

- 요청·메시지마다 INFO 이상을 남기지 않는다. 요청 경로·상태·지연은 이미 트레이스와 지표에 있다.
- demo(AWS)에서 DEBUG를 켜지 않는다. 원인은 지표, 트레이스, WARN·ERROR 로그로 찾는다.
- 게이트웨이 5xx 알림의 `inspect` 명령은 해당 서비스 로그를 `ERROR`로 걸러 보여 준다(`.infra/monitoring/alert-rules.yaml`). ERROR 한 줄 한 줄이 장애 때 읽을 가치가 있어야 한다.
- 의존 서비스가 죽어 있는 동안 요청마다 반복되는 실패(fallback, 열린 서킷)는 WARN이나 DEBUG다. 서킷 상태 전환 로그가 이미 한 번 알려 준다.

---

## 3. 남기면 안 되는 것

| 종류 | 예 | 대신 |
|---|---|---|
| 개인정보 | 이메일, 이름, 전화번호, IP | `memberId`, `fileId` 같은 id |
| 비밀값 | 비밀번호, 세션 id, 인증 코드, 공유 링크 키, presigned URL, 토큰, 쿼리 문자열 전체 | 남기지 않음 |
| 요청·응답 본문, 이벤트 페이로드 전체 | | 필요한 id만 |

`userId`, `traceId`, `spanId`는 모든 줄에 자동으로 붙는다(`UserIdObservationFilter`가 MDC에 넣음, 트레이싱). 메시지에 다시 쓰지 않는다.

---

## 4. 메시지 작성법

```java
log.warn("Pruning versions of fileId={} lost a lock race; retried next run", fileId.value());
log.error("Failed to purge trash root fileId={} namespaceId={}", root.getId(), root.getNamespaceId(), e);
```

- 영어 한 문장, 끝에 마침표 없음.
- 값은 `{}` 자리표시자로 넣는다. 문자열 이어 붙이기는 쓰지 않는다.
  문장만으로 값이 무엇인지 모르면 `key={}`로 쓴다(`fileId={} namespaceId={}`). `Pruned {} file version(s)`처럼 문장이 말해 주면 키는 없어도 된다.
- 예외는 **마지막 인자로 따로** 넘긴다(`e.getMessage()` 아님). 그래야 스택 트레이스가 남는다.
  단, 의존 서비스(Redis, member-service, 열린 서킷)가 죽어 있는 동안 요청마다 반복되는 WARN은 `e.toString()`을 쓴다. 같은 스택 트레이스가 요청마다 찍히면 로그가 넘친다.
- 클래스에 `@Slf4j`(Lombok)를 붙인다. `LoggerFactory`를 직접 쓰지 않고, 포트 인터페이스에 로거를 두지 않는다.
- `[Tag]` 같은 접두어를 붙이지 않는다. 어디서 찍혔는지는 `logger` 필드에 이미 있다.

---

## 5. 테스트로 막는 것

서비스마다 아키텍처 테스트(`HexagonalArchitectureTest`, gateway는 `GatewayArchitectureTest`)가 아래를 검사한다.

| 규칙 | 걸리는 예 |
|---|---|
| `Logger` 필드는 클래스에 있고 이름이 `log`다 | 직접 만든 `logger`·`LOG` 필드, 인터페이스에 둔 로거 |
| `domain/`은 `org.slf4j`에 의존하지 않는다 (gateway 제외, `domain/` 없음) | 도메인 모델에서 로그를 남김 |
