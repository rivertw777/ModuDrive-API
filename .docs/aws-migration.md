# AWS 이관 기술 매핑

로컬 docker compose 인프라를 AWS 관리형 서비스로 옮길 때 무엇으로 대체할지 정리한 문서입니다.
2026-09-23 기준 코드 조사 결과. **확정**: 메시징 SQS, 컴퓨팅 ECS Fargate (2026-09-19). `★`는 아직 결정이 필요한 항목.

---

## 목차

- [1. 한눈에 보기](#1-한눈에-보기)
- [2. 항목별 상세](#2-항목별-상세)
  - [2-1. 컴퓨팅: ECS Fargate (확정)](#2-1-컴퓨팅-ecs-fargate-확정)
  - [2-2. 서비스 간 호출: 고정 URL → ECS Service Connect](#2-2-서비스-간-호출-고정-url--ecs-service-connect)
  - [2-3. 진입점: ALB → gateway-service](#2-3-진입점-alb--gateway-service)
  - [2-4. DB: RDS for PostgreSQL](#2-4-db-rds-for-postgresql)
  - [2-5. Redis → MemoryDB(prod) / ElastiCache(demo) for Valkey](#2-5-redis--memorydbprod--elasticachedemo-for-valkey)
  - [2-6. 메시징: Amazon SQS (확정)](#2-6-메시징-amazon-sqs-확정)
  - [2-7. 파일 저장: Amazon S3](#2-7-파일-저장-amazon-s3)
  - [2-8. 메일 → SES](#2-8-메일--ses)
  - [2-9. 시크릿 → Secrets Manager / SSM Parameter Store](#2-9-시크릿--secrets-manager--ssm-parameter-store)
  - [2-10. ★ 모니터링 · 알림](#2-10--모니터링--알림)
  - [2-11. 네트워크 / 도메인 / 프론트](#2-11-네트워크--도메인--프론트)
  - [2-12. IaC / 배포](#2-12-iac--배포)
  - [2-13. ★ 서비스 간 접근 제어: 서비스별 보안 그룹 (필수)](#2-13--서비스-간-접근-제어-서비스별-보안-그룹-필수)
- [3. 진행 순서와 현황](#3-진행-순서와-현황)
- [4. 결정 현황](#4-결정-현황)

---

## 1. 한눈에 보기

| 현재 (로컬) | AWS 대체 | 앱 쪽 준비 |
|---|---|---|
| docker compose + 이미지 로컬 빌드 | **ECS Fargate** + **ECR** | 필요 없음 (`.docker/Dockerfile` 그대로) |
| 서비스 간 호출: 고정 URL(`clients.<서비스>.url`, compose DNS) | **ECS Service Connect** | ✅ 완료 (#363) — URL 값만 |
| gateway-service (Spring Cloud Gateway) | 그대로 유지 + 앞단 **ALB** | 필요 없음 |
| Postgres 18 (서비스별 DB 3개, Flyway) | **RDS for PostgreSQL** | 필요 없음 (접속 정보만) |
| Redis 7 | **MemoryDB for Valkey**(prod) / **ElastiCache for Valkey**(demo) | ✅ 완료 — TLS(`REDIS_SSL_ENABLED=true`), prod는 클러스터 모드 환경 변수 2개 |
| LocalStack SQS | **Amazon SQS** 표준 큐 | ✅ 완료 (#365, #403) — 큐는 Terraform |
| LocalStack S3 | **Amazon S3** | ✅ 완료 (#364) — 버킷은 Terraform |
| LocalStack SES | **Amazon SES** (API, `SendRawEmail`) | ✅ 완료 — SMTP에서 SES API로 전환 |
| `.docker/.env` 시크릿 | **Secrets Manager** / SSM Parameter Store | 필요 없음 (ECS가 env로 주입) |
| Prometheus / Promtail / Loki / Tempo / Grafana(+ 디스코드 알림) | ★ **AMP + Managed Grafana + X-Ray + CloudWatch Logs** (중앙 ADOT collector) | 앱은 그대로. 알림 규칙은 AMG로 옮김(PromQL 그대로) |
| (없음) | **Route 53 + ACM**, **CloudFront** (WEB 정적 호스팅) | — |
| (없음) | **Terraform** (IaC), **GitHub Actions OIDC** (배포) | — |

---

## 2. 항목별 상세

### 2-1. 컴퓨팅: ECS Fargate (확정)
- EKS 대비 운영 부담이 적고(컨트롤 플레인 비용·업그레이드 없음), 서비스 7개 규모엔 충분.
- 서비스 1개 = ECS Service 1개. 이미지는 ECR, 기존 `.docker/Dockerfile` 재사용.
- 오토스케일: ECS Service Auto Scaling(CPU/요청 수 기준).
- 액추에이터는 앱 포트와 분리된 관리 포트(`management.server.port: 9464`)에 있다 — 보안그룹으로 내부에서만 열기.

### 2-2. 서비스 간 호출: 고정 URL → ECS Service Connect
- **앱 쪽은 완료 (#363 / PR #366)**: 서비스 레지스트리 없이 `application.yml`의 `clients.<서비스>.url`로 호출한다.
  - Feign 5곳: `@FeignClient(name = ..., url = "${clients.<서비스>.url}")`
  - gateway: `RouteConfig`(라우트)와 `WebClientConfig`(auth 호출)가 같은 설정을 읽음
  - 로컬 값은 compose DNS (`http://member-service:10010` 등)
- AWS에선 Service Connect로 같은 형태의 이름을 붙이면 된다 — 이름·포트가 같으면 설정 변경도 없음.

### 2-3. 진입점: ALB → gateway-service
- API Gateway(AWS)로 바꾸면 인증 필터·CSRF 가드·라우팅을 다 옮겨야 해서 이득이 없음. **SCG 유지**.
- 퍼블릭 ALB(HTTPS, ACM 인증서) → gateway 태스크만 퍼블릭 타깃. 나머지 서비스는 프라이빗 서브넷.

### 2-4. DB: RDS for PostgreSQL
- 현재 서비스별 DB 4개 + 서비스별 로그인(#355 / PR #358): `member_db`/`member_service`, `file_db`/`file_service`,
  `notification_db`/`notification_service`, `auth_db`/`auth_service`. 로컬은 `.docker/postgres/postgres_init.sh`가 만든다 — **RDS에선 이 DB·로그인을 따로 만들어야 한다**.
  RDS가 프라이빗이라 Terraform으로는 못 만들고, 같은 스크립트를 **인스턴스마다 일회성 ECS 태스크**(`modudrive-db-init[-<인스턴스>]`, 명령은 출력 `db_init_run_tasks`)로 한 번씩 실행한다. 태스크마다 `DB_SERVICES`로 그 인스턴스에 둘 DB만 만든다(로컬은 비워 둬서 4개 전부).
  RDS 관리자는 superuser가 아니라서 PostgreSQL 16부터 `CREATE DATABASE ... OWNER <로그인>`이 `must be able to SET ROLE`로 실패한다 — 스크립트가 만든 로그인을 관리자에게 `GRANT`해서 해결(로컬 superuser에도 그대로 동작).
- 테이블은 각 서비스가 기동할 때 Flyway가 만든다(#359 / PR #360, `.docs/db-migration.md`) — 별도 작업 없음.
- **인스턴스 구성**은 규모 프로필(2-12)의 `db_instances`로 정한다 — 어느 서비스의 DB를 어느 인스턴스에 둘지까지.
  - `demo`: 인스턴스 1대(db.t4g.micro, 단일 AZ)에 4개 DB.
  - `prod`: **서비스마다 Aurora PostgreSQL 18 클러스터 1개**(`db_engine = "aurora"`) — 쓰기 1대 + 읽기 2대를 AZ 3곳에 하나씩. 저장소는 AZ 3곳에 6벌, 4벌이 확인해야 커밋되므로 AZ 하나를 잃거나 그 AZ와 끊겨도 커밋된 쓰기를 잃지 않고 계속 쓴다. 장애 조치(읽기 노드 승격)는 30초 안팎. 서비스는 **쓰기 엔드포인트로만** 읽고 쓴다 — 읽기 노드의 복제 지연을 보지 않으므로 모든 읽기가 마지막 커밋을 본다. TLS 강제(`rds.force_ssl`, JDBC `sslmode=require`), 백업 35일, Performance Insights. file은 db.r7g.xlarge, 나머지는 db.r7g.large. file-service의 부하가 로그인 경로를 늦추지 않고, 클러스터마다 크기·장애 조치·업그레이드를 따로 한다.
  - 어느 쪽이든 서비스마다 자기 DB·로그인만 쓴다(논리 분리는 같고, `prod`는 인스턴스까지 나눈다). 보안 그룹도 인스턴스마다 — 자기 DB가 있는 서비스와 db-init만 들어온다.
  - 처음 만든 공용 인스턴스는 `file` 이름으로 이어받는다(`moved`, 식별자 `modudrive` 유지). `demo`로 운영하다 `prod`로 바꾸면 member·auth·notification DB를 새 인스턴스로 옮기는 데이터 이전이 필요하다.
- 자동 백업: `demo` 7일, `prod` 35일(특정 시점 복구).
- ⚠️ 상태가 있는 스택에서 `db_engine`을 바꾸면(`rds` ↔ `aurora`) 기존 DB는 **삭제되고** 새 DB가 빈 채로 생긴다 — 스냅샷 복원이나 덤프로 데이터를 옮긴 뒤 바꾼다.

### 2-5. Redis → MemoryDB(prod) / ElastiCache(demo) for Valkey
- 용도별 클러스터로 나눈다 (`terraform/redis.tf`, [006 2-4-6](spec/006-resilience-spec.md#2-4-6-redis-분리)). `prod`는 **MemoryDB**(`redis_engine = "memorydb"` — 쓰기가 Multi-AZ 트랜잭션 로그에 남아 장애 조치에도 유실 없음, 클러스터 모드라 함께 쓰는 키에 해시 태그) `auth`·`member`·`mail`·`storage` 4개(샤드마다 노드 3, `storage`는 샤드 2), `demo`는 로컬처럼 1개(모든 서비스 공용) — `envs/*.tfvars`의 `redis_clusters`. `demo`에 예전 `storage` 클러스터가 떠 있었다면 apply 때 없어진다 — commit되지 않은 업로드 기록·다운로드 한도·zip 토큰만 사라진다(업로드 중이던 블록은 다시 보내면 된다).
  - 모두 `maxmemory-policy noeviction` 파라미터 그룹(ElastiCache `valkey8`·엔진 8.1, MemoryDB `memorydb_valkey7`·엔진 7.3에서 자동 마이너 업그레이드). 기존 클러스터에 적용하면 엔진 버전을 먼저 확인한다.
  - 클러스터마다 보안 그룹·AUTH 토큰(SSM `REDIS_PASSWORD`/`REDIS_PASSWORD_<클러스터>`)이 따로다 — 그 클러스터를 쓰는 서비스만 들어오고 그 토큰만 받는다. 예전 공용 보안 그룹·토큰은 `moved`로 `auth`가 이어받고, `storage`는 새 토큰으로 바뀐다(`auth_token_update_strategy = ROTATE` — 태스크가 새 토큰으로 다시 뜰 때까지 옛 토큰도 통한다).
  - 예전 이름(`redis`, `storage_redis`)의 클러스터는 `moved` 블록으로 `redis["auth"]`·`redis["storage"]`가 이어받는다 — 재생성(전원 로그아웃) 없음. `auth`는 클러스터 ID도 예전 그대로(`modudrive`).
- Valkey는 Redis 호환. **전송 암호화(TLS)를 켠다** — 켜면 TLS 연결만 받는다.
  앱은 `application-redis.yml`의 `ssl.enabled`가 `REDIS_SSL_ENABLED`(기본 false)를 읽으므로, ECS 태스크 정의에 `REDIS_SSL_ENABLED=true`만 넣으면 된다.
  인증서는 Amazon 발급이라 JVM 기본 trust store로 검증된다(SSL bundle 불필요). `REDIS_PASSWORD`는 ElastiCache AUTH 토큰 — AUTH는 TLS가 켜져 있어야 쓸 수 있다.

### 2-6. 메시징: Amazon SQS (확정)
- **앱 쪽은 완료**: SQS 어댑터(#365 / PR #368), 로컬은 LocalStack(#403).
- 선택 이유 (Kafka·RabbitMQ 비교), 큐 설정, task role 권한은 `spec/005-messaging-spec.md` 1장.
- Terraform: `init-aws.sh`의 큐 전부 + `-dlq`(표준), visibility 10초, maxReceiveCount 4 — `.docker/localstack/init-aws.sh`와 똑같이. 목록은 005 7장.

### 2-7. 파일 저장: Amazon S3
- **앱 쪽은 완료 (#364 / PR #367)** — `S3Config`가 환경에 따라 동작이 갈린다:
  - endpoint가 있으면(로컬 LocalStack) `endpointOverride` + path-style, 기동 시 버킷이 없으면 생성
  - access key가 없으면 SDK 기본 체인 → ECS **task role**로 인증
  - 운영 버킷은 Terraform이 만들고, 태스크엔 버킷 생성 권한을 주지 않는다
- 버킷 설정(Terraform): 퍼블릭 액세스 차단, SSE-S3/SSE-KMS 기본 암호화, 수명주기 정책.

### 2-8. 메일 → SES
- SMTP가 아니라 **SES API**(`spring-cloud-aws-starter-ses`, `SendRawEmail`)로 보낸다. AWS에선 `SES_ENDPOINT`를 비우면 실제 SES로, 인증은 task role(`ses:SendRawEmail`) — 관리할 SMTP 비밀번호가 없다.
- 사전 작업: 도메인 인증(DKIM/SPF), **샌드박스 해제 요청**(안 하면 인증된 주소로만 발송 가능).
- **Terraform** — 로컬은 `.docker/localstack/init-aws.sh`가 같은 것을 만든다. 빠지면 서비스가 안 뜨거나 메일이 전부 DLQ로 간다. 체크한 항목은 `terraform/ses.tf`·`sqs.tf`·`dns.tf`에 작성됨(apply 전).
  - [x] SES identity(도메인) + DKIM/SPF 레코드
  - [x] Configuration Set `mail-events` + 이벤트 대상: `SEND` → SNS 토픽 `mail-ses-events`. **없으면 발송이 400(`ConfigurationSetDoesNotExist`)으로 전부 DLQ에 간다.**
  - [x] SQS `mail-ses-events` + `mail-ses-events-dlq`(redrive `maxReceiveCount` 4) — 없으면 mail-service 기동 실패(fail on missing queue)
  - [x] SNS → SQS 구독, **raw message delivery 켬** (리스너가 envelope 없는 SES 이벤트 JSON을 읽는다)
  - [x] `mail-ses-events` 큐 정책: `sqs:SendMessage`를 SNS 서비스 + `aws:SourceArn` = 해당 토픽으로만 허용 — 위조 Send 이벤트로 메일을 "보냄" 처리시켜 막는 것 방지 (#518 보안 리뷰)
  - [ ] mail-service task role: `ses:SendRawEmail`(identity + configuration set 리소스), `mail-ses-events` 수신 권한
  - [ ] 배포 전 실제 SES(샌드박스)로 한 통 보내 `deliveryId` 태그(`<queue>_<outboxId>`)가 거절되지 않는지 확인 — LocalStack은 태그 값을 검증하지 않는다

### 2-9. 시크릿 → Secrets Manager / SSM Parameter Store
- 옮길 것(`.docker/.env.example` 기준):
  - DB: `MEMBER_DB_PASSWORD`, `FILE_DB_PASSWORD`, `NOTIFICATION_DB_PASSWORD`, `AUTH_DB_PASSWORD` (+ RDS 마스터 비밀번호)
  - `REDIS_PASSWORD`, `STORAGE_ENCRYPTION_KEY`
  - `DISCORD_MESSAGING_WEBHOOK_URL`, `DISCORD_SERVICE_WEBHOOK_URL` (알림을 디스코드로 계속 보낼 경우)
- **AWS에선 필요 없는 것**: `SQS_*`, `SES_ENDPOINT`, `STORAGE_S3_ACCESS_KEY`/`SECRET_KEY`(task role로 대체), `LOCALSTACK_AUTH_TOKEN`(로컬 전용).
- ECS 태스크 정의의 `secrets`로 환경변수 주입 → 앱은 지금처럼 `${...}`로 읽음.
- 비용: Parameter Store(SecureString)는 무료, Secrets Manager는 시크릿당 과금 대신 자동 로테이션.
  RDS 비밀번호만 Secrets Manager(로테이션), 나머지는 Parameter Store로 충분.
- `STORAGE_ENCRYPTION_KEY`는 장기적으로 KMS 봉투 암호화 검토.

### 2-10. ★ 모니터링 · 알림

**확정 (2026-10-06): AMP + Amazon Managed Grafana + X-Ray + CloudWatch Logs, 수집은 중앙 ADOT collector 1개.**

선택 기준은 **혼자서도 운영이 간단할 것**과 **지금 만든 것(PromQL 알림 7개, Grafana, OTLP)을 그대로 쓸 것** — 저장소와 Grafana는 전부 관리형으로 두고, 직접 띄우는 건 중앙 ADOT collector ECS 서비스 하나뿐이다.

```
ECS 태스크들 ──OTLP 트레이스──▶ ┌─────────────────────┐ ──▶ X-Ray
            ◀──:9464 수집─────  │ 중앙 ADOT collector │ ──▶ AMP (remote write)
            ──stdout(awslogs)─▶ └─────────────────────┘
                 CloudWatch Logs ◀───────────── Amazon Managed Grafana ──▶ Discord
```

| 신호 | 로컬 | AWS | 수집 |
|---|---|---|---|
| 메트릭 | Prometheus | **Amazon Managed Prometheus (AMP)** | 중앙 ADOT가 ECS observer로 태스크를 찾아 `:9464` 수집 → remote write |
| 트레이스 | otel-collector → Tempo | **X-Ray** | 앱 OTLP → 중앙 ADOT(tail sampling) → X-Ray |
| 로그 | Promtail → Loki | **CloudWatch Logs** | `awslogs` 드라이버 — 사이드카 없음 (Promtail은 docker socket 기반이라 Fargate에서 못 쓴다) |
| 대시보드·알림 | Grafana | **Amazon Managed Grafana (AMG)** | AMP·X-Ray·CloudWatch 데이터소스 |

#### 다른 선택지를 뺀 이유

| 선택지 | 뺀 이유 |
|---|---|
| CloudWatch만 (메트릭·대시보드·알림까지) | 알림 7개를 CloudWatch Alarm으로 다시 써야 한다. Prometheus 지표를 커스텀 메트릭으로 넣으면 레이블 조합마다 과금이라 히스토그램 버킷 × URI × 상태 × 태스크로 비용이 커진다 |
| Grafana 스택 직접 운영 (Loki·Tempo·Prometheus·Grafana on ECS) | 저장소(S3/EFS)·업그레이드·장애 대응을 혼자 떠안는다 — "간단한 운영"과 반대 |
| Grafana Cloud (SaaS) | 운영은 가장 쉽고 도구도 똑같지만, 데이터가 AWS 밖으로 나가 전송 비용이 들고 대용량에서 요금이 빠르게 오른다. AWS 이관 학습 목표와도 어긋난다 |

#### 설계 요점

- **앱은 바뀌지 않는다** — `OTEL_EXPORTER_OTLP_ENDPOINT`를 중앙 collector의 Service Connect 주소로만 바꾼다. 메트릭(`/actuator/prometheus`)·JSON 로그(`traceId`·`userId` 필드)는 그대로.
- **사이드카가 아니라 중앙 collector 1개** — 한 trace의 span이 전부 한 collector로 모여야 tail sampling(에러 + 1초 초과 + 5%)이 지금처럼 동작한다.
  태스크마다 사이드카를 붙이면 span이 흩어져 "에러 trace 전량 보관"이 깨진다. collector가 죽어도 telemetry만 잠시 빠지고 서비스는 영향 없다(앱은 비동기 export, 실패 시 버림).
  **collector를 2대 이상으로 늘릴 땐** 앞단 collector가 `loadbalancing` exporter로 **traceID 기준** 라우팅하는 2단 구성으로 바꾼다 — 처음부터 할 필요는 없다.
- **수집 대상 자동 탐색** — 로컬 `prometheus.yml`은 서비스 7개를 `static_configs`로 박아 두지만, ECS에선 태스크 IP가 바뀌고 서비스당 태스크가 여럿이다.
  ADOT `ecs_observer`가 태스크 정의 라벨로 대상을 찾는다. 알림 메시지의 "서비스"는 `instance`(태스크 IP)에서 뽑으므로 서비스 이름 라벨(`job`·`service`)을 붙이고 템플릿도 그 라벨을 쓰게 바꾼다.
- **알림** — `alerts.yaml`(규칙 7개, Discord 수신처 2개)을 AMG로 옮긴다. PromQL이라 쿼리는 그대로, 바뀌는 건 라벨(위)과 `inspect` 문구(`docker logs` → CloudWatch Logs Insights, TraceQL → X-Ray 검색).
  Discord 웹후크 URL은 Secrets Manager/Parameter Store(2-9).
- **로그 검색** — LogQL 대신 CloudWatch Logs Insights. JSON 필드라 `filter traceId = "..."`, `filter userId = "..."`가 바로 된다.
- **ALB는 trace에 끼지 않는다** — ALB는 W3C `traceparent`가 아니라 `X-Amzn-Trace-Id`를 붙인다. trace는 gateway부터, ALB 구간은 ALB 액세스 로그(S3)로 본다.
- **메트릭 레이블에 userId·fileId 금지** — AMP는 샘플 수 과금. 고유값은 span 태그와 로그에만 둔다(지금 코드도 그렇다).

#### 비용 관리 (MAU 500만 가정)

- **로그가 비용의 대부분이다.** 시작은 CloudWatch Logs 보존 7~14일 + INFO 로그 줄이기(+ Infrequent Access 로그 클래스 검토).
  그래도 크면 **FireLens(Fluent Bit) 2갈래**로 바꾼다: ERROR/WARN·감사 로그 → CloudWatch Logs, 전량 → S3(장기, Athena 조회). 처음부터 하지 않는다.
- **트레이스** — X-Ray는 trace 건수 과금이라 100%는 불가. 지금 tail sampling 정책을 그대로 가져간다.
- **메트릭** — 수집 주기 15초 → 30~60초 검토, 안 쓰는 지표는 collector에서 drop.
- **AMG** — 사용자 수 과금(혼자면 월 $9 수준), 로그인에 IAM Identity Center 1회 설정 필요.

#### 이관 때 확인할 것 (불확실)

- [ ] X-Ray가 앱의 W3C 형식 trace ID(첫 32비트가 타임스탬프가 아님)를 그대로 받는지 — 안 되면 ADOT/SDK에 X-Ray ID 생성기 설정
- [ ] AMP가 exemplar를 저장하고 AMG에서 X-Ray로 링크되는지 — 안 되면 알림 문구의 trace 검색 안내로 대체
- [ ] 단일 collector의 처리량 한계(CPU·메모리)와 tail sampling `decision_wait`(30s) 동안의 메모리 — 넘으면 위 2단 구성

#### Terraform으로 만들 것

- [ ] AMP 워크스페이스
- [ ] AMG 워크스페이스 + IAM Identity Center 사용자, 데이터소스(AMP·X-Ray·CloudWatch) 권한 역할
- [ ] 중앙 ADOT collector ECS 서비스 + 설정(SSM Parameter): `otlp` 수신 → `tail_sampling` → `awsxray`, `prometheus`(ecs_observer) → `prometheusremotewrite`(SigV4)
- [ ] ADOT task role: `aps:RemoteWrite`, `xray:PutTraceSegments`·`xray:PutTelemetryRecords`, ecs_observer용 `ecs:ListTasks`·`ecs:DescribeTasks`·`ecs:DescribeTaskDefinition`·`ec2:DescribeInstances`
- [ ] 서비스 태스크 정의: `awslogs` 로그 드라이버 + 로그 그룹(보존 기간), 수집 대상 라벨, `OTEL_EXPORTER_OTLP_ENDPOINT`
- [ ] 보안 그룹: ADOT → 각 서비스 `9464`, 각 서비스 → ADOT `4318` (2-13)
- [ ] AWS 기본 지표를 AMG(CloudWatch 데이터소스)에 연결 + 알림 추가:
  - SQS `ApproximateAgeOfOldestMessage` — 컨슈머가 느려져 큐가 쌓이는 것 (지금은 DLQ 개수만 본다)
  - S3 4xx/5xx·`503 SlowDown`·`FirstByteLatency` (버킷 요청 지표는 켜야 나온다, 유료)
  - ALB 5xx·`TargetResponseTime`, CloudFront 캐시 히트율·오리진 에러율, ECS Container Insights(CPU·메모리)
- [ ] (선택) 알림 수신처를 디스코드로 계속 둘지 — 실무는 Slack + PagerDuty 같은 온콜 도구

### 2-11. 네트워크 / 도메인 / 프론트
- VPC: 퍼블릭 서브넷(ALB), 프라이빗 서브넷(ECS, RDS, ElastiCache).
- NAT Gateway는 비싸다 — S3/ECR/SQS/Secrets Manager/CloudWatch는 **VPC 엔드포인트**로 돌리면 NAT 트래픽 절감. 둘 다 `prod`에서만 켠다(2-12). `demo`는 NAT 게이트웨이 대신 **NAT 인스턴스 1대**(t4g.nano, fck-nat — 공인 IP 포함 월 ~$8)를 두고 무료인 S3 게이트웨이 엔드포인트만 쓴다. 태스크는 어느 쪽이든 프라이빗 서브넷이고 공인 IP가 없다 — 태스크마다 공인 IPv4를 붙이면(7개 ~$26) NAT 인스턴스보다 비싸다.
- Route 53(도메인) + ACM(인증서). ModuDrive-WEB은 **S3 + CloudFront** 정적 호스팅,
  `CLIENT_URL`/CORS/쿠키 도메인 재설정 필요.
- WEB의 **CSP**는 CloudFront **응답 헤더 정책**으로 붙인다. 정책 문자열은 ModuDrive-WEB `vite.config.ts`의 `contentSecurityPolicy()`가 원본(`vite preview`가 같은 헤더를 보냄) — `connect-src`에 API 도메인을 넣고, 둘을 같이 고친다.
- ⚠️ **WEB과 API는 같은 등록 도메인(사이트)에 있어야 한다** (예: `app.modudrive.com` / `api.modudrive.com`, 또는 CloudFront path behavior로 `/api/*`를 같은 origin에 붙임). 세션 쿠키가 `SameSite=Strict` + host-only라서 ([004 인증](spec/004-auth-spec.md) 1-1-2), 사이트가 다르면 로그인은 200인데 이후 요청에 쿠키가 안 실려 전부 401이 된다. `*.cloudfront.net`·`*.elb.amazonaws.com` 기본 도메인은 Public Suffix List에 있어 서로 다른 사이트로 취급되므로 **커스텀 도메인 필수**.

### 2-12. IaC / 배포
- **Terraform**으로 전부 코드화 (콘솔 수작업 금지 — 재현·리뷰 불가). 코드는 `terraform/` 루트 모듈 하나 — 파일을 관심사별로 나눈다
  (`network.tf`·`security_groups.tf`·`rds.tf`·`redis.tf`·`s3.tf`·`sqs.tf`·`ses.tf`·`dns.tf`·`secrets.tf`). 환경은 하나(운영)뿐이라 모듈·워크스페이스로 나누지 않는다.
- 상태는 S3 백엔드(`use_lockfile`로 S3 자체 잠금, DynamoDB 불필요). 상태 버킷은 Terraform이 자기 자신을 관리할 수 없으니 **한 번만 손으로** 만든다(버전 관리 켬).
  ```bash
  cp terraform/backend.hcl.example terraform/backend.hcl   # 버킷 이름 채우기
  cd terraform && terraform init -backend-config=backend.hcl && terraform plan -var-file=envs/demo.tfvars
  # 계정 없이 문법만: terraform init -backend=false && terraform validate
  ```
- **규모 프로필** — 값 파일 하나로 고른다: `terraform/envs/demo.tfvars`(실제로 띄우는 포트폴리오 데모) 또는 `terraform/envs/prod.tfvars`(MAU 500만 목표의 운영 설계). 아키텍처(서비스·보안 그룹·큐·IAM)는 같고, 돈이 드는 것과 이중화만 다르다. 크기 변수(`variables.tf`)에는 기본값이 없어서 `-var-file` 없이는 plan이 안 된다 — 두 파일을 섞어 쓰지 않는다. 상태는 하나라 둘을 동시에 띄우지 않는다.

  | | `demo` (실제 실행) | `prod` (MAU 500만 목표 설계) |
  |---|---|---|
  | AZ | 2개 (`az_count = 2` — 3번째는 ALB 공인 IP만 늘림) | **3개** — AZ 하나를 잃거나 끊겨도 과반이 남는다 |
  | 태스크 위치 | 프라이빗 서브넷 + **NAT 인스턴스 1대** (t4g.nano, `nat = "instance"`) | 프라이빗 서브넷 + AZ마다 NAT 게이트웨이 (`nat = "gateway"`) |
  | VPC 인터페이스 엔드포인트 | 없음 (S3 게이트웨이만) | SQS·ECR·logs·SSM·Secrets |
  | ECS | 서비스당 1개 고정(오토스케일 없음), 전부 0.25 vCPU/1 GB, **Fargate Spot** | **서비스마다 최소 3개**(AZ마다 1개), gateway·auth·file 1 vCPU/2 GB, storage 2 vCPU/4 GB, 일반 Fargate |
  | DB | RDS 1대에 4개 DB — db.t4g.micro, 단일 AZ, 삭제 방지 끔 | **서비스마다 Aurora PostgreSQL 클러스터** — 쓰기 1 + 읽기 2(AZ마다), file db.r7g.xlarge·나머지 db.r7g.large, TLS 강제, 백업 35일, 삭제 방지 |
  | Redis | ElastiCache for Valkey 1개(모든 서비스 공용), cache.t4g.micro 1노드 | **MemoryDB for Valkey** 용도별 4개 — 샤드마다 노드 3(AZ마다), `storage`는 샤드 2, db.r7g.large(`storage` xlarge), 스냅샷 35일 ([006 2-4-6](spec/006-resilience-spec.md#2-4-6-redis-분리)) |
  | Container Insights | 끔 | 켬 |
  | 대략 비용(서울, 트래픽 전) | 월 $108 안팎 — 태스크 7개 Spot ~$26, ALB ~$22 + 공인 IPv4 2개 ~$7, RDS ~$21, ElastiCache ~$18, NAT 인스턴스 ~$8(공인 IP 포함), 로그 등 ~$5 | **비용은 설계 기준이 아니다** — 보안·일관성·가용성·분할 내성이 먼저. 참고로 최소 규모에서도 월 수천 달러(Aurora 노드 12대가 대부분) |

  - `demo`는 **시험용 — 줄일 수 있는 비용은 다 줄인다.** 격리·이중화는 `prod` 설계로 보여 준다.
  - `demo`가 감수하는 것: 바깥으로 나가는 통신이 NAT 인스턴스 1대에 걸림(멈추면 화면·API는 그대로지만 이벤트·메일·새 태스크 기동이 복구될 때까지 기다림 — EC2 자동 복구), Spot 회수·재배포 때 잠깐 끊김, Redis 재시작 시 전원 로그아웃.
  - `prod` 숫자는 MAU 500만의 **출발점**이지 측정값이 아니다 — 부하 테스트와 오토스케일링 기록으로 맞춘다.
  - 바꾸면 ECS 서비스(Spot ↔ 일반)와 서브넷이 교체되고 RDS 클래스 변경은 재시작이 따른다 — 사용자가 없을 때 바꾼다.
- 도메인은 `domain_name` 변수 — 비워 두면(기본 null) Route 53 영역·SES identity·DKIM/MAIL FROM 레코드를 만들지 않는다. 넣고 apply한 뒤 출력 `name_servers`를 도메인 등록 업체에 설정한다.
- 상태 파일에 `random_password`로 만든 비밀번호가 평문으로 들어간다 — 상태 버킷은 암호화 + 접근을 배포 역할로만 제한한다.
- **첫 apply 순서** — 서비스는 ECR 이미지와 DB가 있어야 뜬다:
  (아래 모든 명령에 `-var-file=envs/demo.tfvars` 또는 `-var-file=envs/prod.tfvars`)
  1. `terraform apply -target=aws_ecr_repository.service -var image_tag=init` — 저장소만 먼저
  2. 서비스 이미지 7개를 빌드해 같은 태그로 ECR에 푸시 (3장 3단계 CI/CD가 생기면 CI가 한다)
  3. `terraform apply -target=aws_ecs_task_definition.db_init -var image_tag=<태그>` → 출력 `db_init_run_tasks`의 명령을 인스턴스마다 실행 (DB·로그인 생성, 한 번만)
  4. `terraform apply -var image_tag=<태그>` — 나머지 전부. 이후 배포는 4번만 반복
- 운영은 `SPRING_PROFILES_ACTIVE=prod` — `dev`는 테스트 계정 시드(`db/seed`)와 Swagger를 켠다.
- 트레이스 export는 `MANAGEMENT_TRACING_EXPORT_ENABLED=false`로 꺼 둔다 — 모니터링 단계(2-10)에서 중앙 ADOT를 만들면 지우고 `OTEL_EXPORTER_OTLP_ENDPOINT`를 넣는다.
- GitHub Actions 워크플로는 아직 없다 (3장 3단계).
- GitHub Actions: OIDC로 AWS 인증(장기 키 없음) → 이미지 빌드 → ECR 푸시 → ECS 서비스 업데이트.

### 2-13. ★ 서비스 간 접근 제어: 서비스별 보안 그룹 (필수)

**이관할 때 반드시 적용한다.** 지금 서비스 간 호출(`/internal/**`)에는 인증이 없다 — 게이트웨이가 이 경로를 외부에 열지 않는 것에만 기대므로, 내부 네트워크에 들어온 누구나 모든 서비스의 `/internal` 경로를 부를 수 있다 ([004 인증](spec/004-auth-spec.md) TODO). 서비스별 보안 그룹은 **"누가 누구를 부를 수 있나"를 네트워크에서 강제**해서 이 범위를 호출 관계 그대로 줄인다.

- 서비스(ECS 서비스)마다 보안 그룹을 **하나씩** 만든다. 여러 서비스가 보안 그룹을 공유하지 않는다.
- 인바운드는 **"허용할 호출자의 보안 그룹 → 내 앱 포트"**만 연다. CIDR(`10.0.0.0/16` 같은 대역)로 열지 않는다 — 대역으로 열면 같은 VPC 안의 모든 태스크가 닿는다.
- 효과 예: 게이트웨이가 뚫려도 네트워크상 member·file·storage의 `/internal`에는 닿지 않는다 (게이트웨이는 그 서비스들의 **공개 API 포트**로만 라우팅하고, 그 요청은 게이트웨이 세션 확인을 거친다).

#### 인바운드 규칙 (2026-10-08 코드 기준 호출 관계 — `terraform/security_groups.tf`와 같다)

| 보안 그룹 | 허용할 출발지 | 포트 | 이유 (코드) |
|---|---|---|---|
| `alb-sg` | 인터넷 `0.0.0.0/0` | 443 | 유일한 외부 진입점 |
| `gateway-sg` | `alb-sg` | 10001 | ALB → gateway |
| `auth-sg` | `gateway-sg` | 10011 | 라우팅(`/api/v1/auth/**`) + 세션 확인(`AuthClient`) |
| `member-sg` | `gateway-sg`, `auth-sg`, `file-sg` | 10010 | 라우팅 / 로그인 확인(auth `MemberClient`) / 공유 대상 조회(file `MemberClient`) |
| `file-sg` | `gateway-sg`, `storage-sg` | 10012 | 라우팅 / 버전·zip 항목·커밋된 블록 조회(storage `FileClient`) |
| `storage-sg` | `gateway-sg`, `file-sg` | 10013 | 라우팅 / commit 때 올라온 블록 조회(file `StorageClient`). 블록 삭제는 SQS(`storage-blocks-purge-requested`) |
| `notification-sg` | `gateway-sg` | 10015 | 라우팅(`/api/v1/notifications/**`) |
| `mail-sg` | **없음** | — | HTTP API가 없다 (SQS 소비만). 게이트웨이도 라우팅하지 않는다 |

데이터 저장소도 같은 원칙으로 **쓰는 서비스만** 연다.

| 보안 그룹 | 허용할 출발지 | 포트 |
|---|---|---|
| `rds-sg` (인스턴스마다) | 그 인스턴스에 DB가 있는 서비스의 보안 그룹만 — `prod`는 서비스마다 자기 인스턴스에만 닿는다 (각자 자기 DB 로그인만 가능 — 2-4) | 5432 |
| `redis-sg` (클러스터마다) | 그 클러스터를 쓰는 서비스의 보안 그룹만 — `prod`는 `auth-sg`→auth, `member-sg`→member, `mail-sg`→mail, `storage-sg`→storage 클러스터 ([006 2-4-6](spec/006-resilience-spec.md#2-4-6-redis-분리)) | 6379 |

- 관리 포트(9464, actuator·Prometheus)는 중앙 ADOT collector의 보안 그룹에서만 연다 — 예외로 gateway의 9464는 ALB 헬스 체크용으로 `alb-sg`에도 연다. DB 생성 일회성 태스크는 전용 `db-init-sg`로 `rds-sg`에 들어간다. 반대로 ADOT의 `4318`(OTLP)은 서비스 보안 그룹들에서만 연다 (2-10).
- 아웃바운드: SQS·S3·Secrets Manager·ECR·CloudWatch는 VPC 엔드포인트로 가고(2-11), SES(메일)와 외부 알림(디스코드)만 NAT로 나간다. 아웃바운드도 좁히려면 엔드포인트용 보안 그룹과 NAT 경로만 허용한다.
- ECS Service Connect를 써도 그대로 적용된다 — 호출은 호출하는 태스크의 네트워크 인터페이스에서 출발하므로, 받는 쪽 보안 그룹에서 출발지 보안 그룹으로 걸러진다.

#### 유지 규칙

- **새 Feign/WebClient 호출을 추가하면 이 표와 Terraform 규칙을 같이 고친다.** 안 고치면 AWS에서만 타임아웃이 난다 (로컬 compose는 전부 열려 있어 문제가 안 보인다).
- 표는 `@FeignClient`·`clients.<서비스>.url` 목록과 맞아야 한다: `grep -rn "@FeignClient\|clients\." services/*/src/main`.

#### 이후 단계 (선택)

보안 그룹은 **네트워크 수준**이라 "허용된 서비스가 허용되지 않은 경로를 부르는 것"은 못 막는다 (예: file이 member의 로그인 확인 API 호출). 경로 단위 권한이 필요해지면 **VPC Lattice + IAM 인증**을 도입한다 — 각 태스크 역할로 SigV4 서명, Lattice 정책으로 "gateway 역할만 auth의 세션 확인 허용" 같은 규칙. 서비스별 요금과 Service Connect 설계 변경이 따르므로 이관이 안정된 뒤 검토한다.

---

### 2-14. 컨테이너 강화 (모든 환경)

비용이 들지 않으니 `demo`·`prod`·로컬 모두 같다.

- 이미지(`.docker/Dockerfile`)는 **JRE**(`eclipse-temurin:25-jre-alpine`) — 실행 중인 서비스에는 컴파일러·`jcmd`가 필요 없다.
- **비루트 사용자**(`app`)로 실행한다. 프로세스가 뚫려도 이미지 파일을 바꾸거나 낮은 포트를 열 수 없다.
- ECS 태스크(`ecs.tf`): **루트 파일시스템 읽기 전용**(`readonlyRootFilesystem`), `/tmp`만 태스크 임시 저장소 볼륨으로 쓰기 가능(Tomcat multipart 임시 파일·JVM), **Linux capability 전부 제거**(`drop = ["ALL"]`).
- Fargate는 이름만 있는 볼륨을 **root 소유 `0755`**로 마운트한다 — 그대로면 비루트 사용자가 `/tmp`에 못 써 Tomcat이 뜨지 못한다. 이미지가 `/tmp`를 `VOLUME`으로 선언하고 `1777`로 두면 Fargate가 그 권한을 볼륨에 복사한다. 로컬 compose에서는 컨테이너마다 익명 볼륨이 생기므로 `docker compose down -v`로 함께 지운다.
- 로컬에서 Fargate와 같은 조건(`--read-only -v /tmp --cap-drop ALL --security-opt no-new-privileges`)으로 7개 서비스를 띄워 기동·헬스 체크·storage 블록 업로드(multipart 3 MB)를 확인했다. (`--tmpfs /tmp`는 누구나 쓸 수 있는 tmpfs라 위 권한 문제를 가리므로 쓰지 않는다.)

## 3. 진행 순서와 현황

1. **이식성 작업** (로컬에서 검증 가능한 것들) — ✅ 완료
   - 서비스별 DB 분리 — #355 / PR #358
   - Flyway 스키마 관리 — #359 / PR #360
   - 서비스 레지스트리 제거 → 고정 URL 호출 — #363 / PR #366
   - `S3Config` task role 대응 — #364 / PR #367
   - SQS 어댑터 — #365 / PR #368
   - 로컬 SQS·S3를 LocalStack으로 통일 — #403 / PR #404
2. 🔄 **Terraform 기반 인프라** — 코드만 작성·`validate`, 실제 apply는 AWS 계정 준비 후
   - ✅ 2-1단계: 상태 백엔드, VPC(서브넷·NAT·VPC 엔드포인트), **서비스별 보안 그룹 (2-13)**, RDS, ElastiCache(Valkey, TLS), S3, SQS(큐·DLQ), SES(Configuration Set·Send 이벤트·identity·DKIM·MAIL FROM), Route 53 영역, SSM 시크릿
   - ✅ 2-2단계: ECR, ECS 클러스터·서비스 7개(Service Connect, 태스크 정의 env/secrets, CPU 오토스케일링, 배포 실패 자동 롤백), ALB + ACM(도메인 있을 때), task role(SQS·S3·SES 최소 권한), DB·로그인 생성 일회성 태스크
3. ⬜ **CI/CD** — GitHub Actions → ECR → ECS
4. ⬜ **모니터링 · 알림** — AMP·AMG·중앙 ADOT·로그 그룹, 알림 7개 이전 (2-10)
5. ⬜ **WEB** — S3 + CloudFront, 도메인 연결

---

## 4. 결정 현황

| 항목 | 결정 |
|---|---|
| 메시징 | **SQS** (2026-09-19 확정) |
| 컴퓨팅 | **ECS Fargate** (2026-09-19 확정) |
| 서비스 간 접근 제어 | **서비스별 보안 그룹** (2026-09-25 확정, 2-13). 공용 토큰은 2026-09-27 제거. VPC Lattice + IAM은 이관 안정화 후 검토 |
| ★ 모니터링 · 알림 | **AMP + Managed Grafana + X-Ray + CloudWatch Logs**, 수집은 중앙 ADOT collector 1개 (2026-10-06 확정, 2-10). 로그 2갈래(FireLens→S3)·collector 2단은 필요해질 때 |
