# 모니터링(Observability) 구조

메트릭·로그·트레이스를 모으는 로컬 dev 모니터링 스택을 정리한 문서입니다. 코드가 기준이고 이 문서는
2026-09-19 시점 스냅샷이니, 설정을 크게 바꿀 때는 같이 갱신할 것.

---

## 목차

- [1. 한눈에 보기](#1-한눈에-보기)
- [2. 구성 파일](#2-구성-파일)
- [3. 컴포넌트별 정리](#3-컴포넌트별-정리)
- [4. 실행](#4-실행)
- [5. 운영 메모 / 트러블슈팅](#5-운영-메모--트러블슈팅)

---

## 1. 한눈에 보기

```
                 ┌──────────── Spring 서비스 (gateway/member/auth/file/storage/mail/notification) ────────────┐
                 │  :9464/actuator/prometheus      stdout 로그 "[app,traceId,spanId]"      OTLP HTTP 트레이스   │
                 └───────┬──────────────────────────────────┬─────────────────────────────────┬──────────────┘
                         │ pull (15s)                       │ docker 로그 파일                  │ push :4318
                         ▼                                  ▼                                 ▼
                    Prometheus                          Promtail ──push──▶ Loki          otel-collector
                         │                                                  │           (tail sampling)
                         │                                                  │                 │ OTLP gRPC :4317
                         │                                                  │                 ▼
                         └──────────────────────▶  Grafana  ◀───────────────┴────────────── Tempo
                                                (localhost:3001)
```

| 신호 | 수집 | 저장 | 보는 곳 |
|---|---|---|---|
| 메트릭 | Prometheus가 각 서비스 `:9464/actuator/prometheus`를 15초마다 스크레이프 | Prometheus TSDB (`prometheus_data`) | Grafana → Prometheus, 또는 `localhost:9090` |
| 로그 | Promtail이 docker socket으로 컨테이너를 자동 발견해서 stdout을 긁음 | Loki (`loki_data`, 72h 보존) | Grafana → Loki |
| 트레이스 | 서비스 → otel-collector(OTLP HTTP) → tail sampling → Tempo | Tempo (`tempo_data`, 로컬 파일시스템) | Grafana → Tempo |

---

## 2. 구성 파일

| 파일 | 역할 |
|---|---|
| `.docker/docker-compose.observability.yml` | 스택 전체 (compose 프로젝트명 `modudrive-observability`) |
| `.docker/observability/*.yaml` | 각 컴포넌트 설정 — 디렉터리째 `/etc/modudrive`로 마운트 |
| `.docker/observability/grafana/datasources/datasources.yaml` | Grafana 데이터소스 프로비저닝 (Prometheus/Tempo/Loki + 상호 링크) |
| `.docker/observability/grafana/alerting/alerts.yaml` | 알림 규칙 3개(outbox 2 + 서비스 다운) + Discord 수신처 ([.docs/spec/discord-alert-spec.md](spec/discord-alert-spec.md)) |
| `common/infrastructure/observability` | 앱 쪽 공통 모듈 (Java 코드 없음, 의존성 + `application-observability.yml`만) |

앱 쪽 공통 모듈은 모든 서비스가 의존하고, 각 서비스 `application.yml`의
`spring.config.import`로 `classpath:application-observability.yml`을 불러온다.

- 의존성: `spring-boot-micrometer-tracing-opentelemetry`(Boot 4 트레이싱 auto-config), `micrometer-tracing-bridge-otel`,
  `opentelemetry-exporter-otlp`, `micrometer-registry-prometheus`
- OTLP 엔드포인트 속성은 Boot 4 이름인 `management.opentelemetry.tracing.export.otlp.endpoint`
- `management.server.port: 9464` — 액추에이터를 앱 포트와 분리. 호스트에 퍼블리시 안 함(내부 네트워크 전용)
- 노출 엔드포인트: `health`, `prometheus`만
- `management.tracing.sampling.probability: 1.0` — 앱은 전량 export, 샘플링은 collector가 결정
- `logging.pattern.correlation` — 로그 줄에 `[앱이름,traceId,spanId]`를 찍음 (Loki→Tempo 링크의 근거)
- SQS observation 켬(`application-sqs.yml`) — 프로듀서→컨슈머로 trace가 이어지게 (`.docs/spec/messaging-spec.md` 6장)

---

## 3. 컴포넌트별 정리

### Prometheus (`prom/prometheus`, `127.0.0.1:9090`)
- 스크레이프 대상 포트 9464는 호스트에 퍼블리시하지 않음 — docker 네트워크 안에서만 접근 가능.
  그래서 액추에이터엔 앱 인증을 걸지 않는다(네트워크 격리가 보안). gateway는 WebFlux라 Security 필터가
  관리 포트에도 걸리므로 `SecurityConfig`에서 `/actuator/**`를 permitAll 해둠 — 관리 포트가 분리돼 있어서
  공개 앱 포트(10001)에선 `/actuator/**`가 어차피 404라 외부 노출은 없다.
- `prometheus.yml`에 서비스 7개를 `static_configs`로 박아둠 (`<service>:9464`). **서비스를 추가하면 여기도 추가해야 함.**
- 스케일아웃하면 컨테이너 DNS가 한 개만 잡힐 수 있음 — 그땐 `dns_sd_configs`/`docker_sd_configs`로 바꿀 것.

### Promtail (`grafana/promtail`) + Loki (`grafana/loki`)
- Promtail은 `docker_sd_configs`로 컨테이너를 5초마다 재발견 → 서비스가 몇 개 뜨든 설정 변경 불필요.
- 라벨: `container`(컨테이너 이름), `service`(compose 서비스 이름). Grafana에서 `{service="file-service"}` 식으로 조회.
- infra/observability 컨테이너 로그(postgres, localstack, loki 자신 등)도 전부 수집됨.
- Loki는 단일 노드, 파일시스템 저장, `retention_period: 72h` + compactor `retention_enabled`로 실제 삭제.
- Promtail은 `/var/run/docker.sock`을 마운트함 — 사실상 호스트 root 권한. **로컬 dev 전용.**

### otel-collector (`otel/opentelemetry-collector-contrib`, `127.0.0.1:4317/4318`)
- traces 파이프라인만 있음: `otlp` 수신 → `tail_sampling` → `batch` → Tempo.
- tail sampling 정책 (하나라도 걸리면 저장):
  - `errors` — 에러 상태 span 포함
  - `slow-requests` — 1초 초과
  - `baseline-sample` — 나머지 중 5%
- `decision_wait: 30s` — gateway timelimiter(15s)보다 길게 잡아서 느린 요청의 늦은 span까지 모은 뒤 판단.
- 메트릭 파이프라인은 없음 (메트릭은 Prometheus pull).
- 호스트 포트는 loopback만 — IDE에서 직접 띄운 서비스를 붙일 때용 (`OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318`).

### Tempo (`grafana/tempo`)
- monolithic 모드, 로컬 파일시스템 백엔드(`/var/tempo`).
- 운영 전환 시 S3 백엔드로 교체 — 주석 참고. storage-service 권한 재사용 금지, 전용 버킷·IAM 발급.

### Grafana (`grafana/grafana`, `127.0.0.1:3001`)
- 3000은 ModuDrive-WEB(Vite)이 쓰므로 3001.
- 익명 Viewer 접근 허용, admin 비밀번호는 `GRAFANA_ADMIN_PASSWORD`.
- 데이터소스 상호 링크:
  - **Loki → Tempo**: 로그 줄의 `[app,<32hex traceId>,<16hex spanId>]`를 정규식으로 뽑아 TraceID 링크 생성
  - **Tempo → Loki**: trace에서 해당 traceId 로그로 점프 (`tracesToLogsV2`)
- 대시보드는 프로비저닝 안 함 — Explore에서 직접 조회.
- 알림은 Grafana 내장 기능으로 처리 — Alertmanager 컨테이너 없음. 규칙·수신처·정책 모두
  `provisioning/alerting`의 파일이라 UI에서 고칠 수 없고 `make reset`에도 살아남는다.
  규칙·문구·수신처는 [.docs/spec/discord-alert-spec.md](spec/discord-alert-spec.md)가 기준.

---

## 4. 실행

```bash
make observability   # 스택만 기동 (modudrive_network 필요 → make network가 선행)
make service         # infra + observability + 서비스 전부
make reset           # 모든 볼륨 삭제 후 재기동 (메트릭/로그/트레이스 데이터도 날아감)
```

---

## 5. 운영 메모 / 트러블슈팅

### 설정은 디렉터리 단위로 마운트한다 (2026-09-19 promtail 무한 재시작 원인)
예전엔 `./observability/promtail-config.yaml:/etc/promtail/config.yaml`처럼 **파일 단위** bind mount였다.
단일 파일 bind mount는 컨테이너 생성 시점의 inode에 묶이는데, 에디터 저장·`git checkout`·`sed -i`는
파일을 새 inode로 **교체**한다. 그러면 컨테이너는 옛(또는 깨진) 내용을 보거나, 재시작 시 마운트 소스를
못 찾아 기동에 실패한다. 실제로 promtail이 `field /.server not found`로 파싱 실패 → `restart: unless-stopped`
때문에 무한 재시작했고, prometheus도 설정 수정 후 `restart`하면 Exit 127로 죽었다.

지금은 `./observability` 디렉터리 전체를 `/etc/modudrive`로 마운트하고 각 컴포넌트가 `--config`
플래그로 그 안의 파일을 가리킨다. 설정을 고친 뒤엔 `docker compose ... restart <서비스>`만 하면 반영된다.
**새 설정 파일을 추가할 때도 파일 단위 마운트로 되돌리지 말 것.**

### 트레이스가 하나도 안 쌓이던 문제 (2026-09-19 해결)
Tempo 0건, 로그의 traceId 칸 공란이던 원인은 Spring Boot 4 마이그레이션 잔재 두 가지였다.
1. **auto-config 모듈 누락** — Boot 4는 auto-config를 기술별 모듈로 쪼갰다. `micrometer-tracing-bridge-otel`만
   있고 `spring-boot-micrometer-tracing-opentelemetry`가 없어서 Tracer 빈 자체가 안 만들어졌다(에러도 없음).
2. **속성 이름 변경** — Boot 3의 `management.otlp.tracing.endpoint`가 Boot 4에서
   `management.opentelemetry.tracing.export.otlp.endpoint`로 바뀌어서 설정이 조용히 무시됐다.

추가로 **Feign 호출(auth → member 등)은 `io.github.openfeign:feign-micrometer`가 있어야** 계측되고 `traceparent`가
전파된다 (`common:infrastructure:spring-cloud`에 추가). 없으면 호출받는 서비스에서 트레이스가 새로 시작된다.
gateway → 서비스(WebClient/Netty)와 SQS(outbox 헤더 저장 → relay Observation → 컨슈머)는 별도 설정 없이 이어진다.

검증된 경로 (한 트레이스로 묶임):
- `POST /api/v1/auth/login` — gateway → auth → (Feign) member
- `POST /api/v1/member/verify-email/request` — gateway → member → outbox relay → SQS → mail-service

확인 팁: tail sampling 때문에 정상 요청은 5%만 남는다. 테스트할 땐 collector 설정에서
`sampling_percentage: 100`, `decision_wait: 5s`로 잠깐 바꾸고 `restart otel-collector` → 끝나면 원복.

### 알려진 갭
- gateway는 Prometheus의 `/actuator/prometheus` 스크레이프(15초마다)도 트레이스로 남긴다. 5% baseline에 걸리면
  Tempo에 잡음처럼 보일 수 있음 — 거슬리면 `ObservationPredicate`로 actuator 경로를 제외.
- otel-collector 설정의 `otlp/tempo` exporter 이름은 deprecated 경고가 뜸(`otlp_grpc` 권장) — 동작엔 문제 없음.
