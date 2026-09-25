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
  - [2-5. Redis → ElastiCache for Valkey](#2-5-redis--elasticache-for-valkey)
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
| Redis 7 | **ElastiCache for Valkey** | ⬜ TLS 설정 추가 |
| LocalStack SQS | **Amazon SQS** 표준 큐 | ✅ 완료 (#365, #403) — 큐는 Terraform |
| LocalStack S3 | **Amazon S3** | ✅ 완료 (#364) — 버킷은 Terraform |
| SMTP (`MAIL_HOST`) | **SES** (SMTP 인터페이스) | 필요 없음 (설정만) |
| `.docker/.env` 시크릿 | **Secrets Manager** / SSM Parameter Store | 필요 없음 (ECS가 env로 주입) |
| Prometheus / Promtail / Loki / Tempo / Grafana(+ 디스코드 알림) | ★ **CloudWatch + X-Ray (ADOT)** 또는 Grafana 스택 자체 호스팅 | 앱은 OTLP 그대로. 알림 규칙은 옮겨야 함 |
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
- 현재 서비스별 DB 3개 + 서비스별 로그인(#355 / PR #358): `member_db`/`member_service`, `file_db`/`file_service`,
  `notification_db`/`notification_service`. 로컬은 `.docker/postgres/postgres_init.sh`가 만든다 — **RDS에선 이 DB·로그인을 따로 만들어야 한다**.
- 테이블은 각 서비스가 기동할 때 Flyway가 만든다(#359 / PR #360, `.docs/db-migration.md`) — 별도 작업 없음.
- 처음엔 단일 인스턴스(db.t4g 계열) + 자동 백업. 트래픽 늘면 Multi-AZ → Aurora 검토.

### 2-5. Redis → ElastiCache for Valkey
- 사용처: auth(토큰), member(인증 코드), storage(다운로드 쿼터), mail(메시지 중복 처리 방지).
- Valkey는 Redis 호환. 전송 암호화(TLS)를 켜면 `application-redis.yml`에 `spring.data.redis.ssl.enabled: true` 추가 필요 — **현재 설정 없음**.

### 2-6. 메시징: Amazon SQS (확정)
- **앱 쪽은 완료**: SQS 어댑터(#365 / PR #368), 로컬은 LocalStack(#403).
- 선택 이유 (Kafka·RabbitMQ 비교), 큐 설정, task role 권한은 `spec/005-messaging-spec.md` 1장.
- Terraform: 큐 4개 + `-dlq`(표준), visibility 10초, maxReceiveCount 4 — `.docker/localstack/init-aws.sh`와 똑같이.

### 2-7. 파일 저장: Amazon S3
- **앱 쪽은 완료 (#364 / PR #367)** — `S3Config`가 환경에 따라 동작이 갈린다:
  - endpoint가 있으면(로컬 LocalStack) `endpointOverride` + path-style, 기동 시 버킷이 없으면 생성
  - access key가 없으면 SDK 기본 체인 → ECS **task role**로 인증
  - 운영 버킷은 Terraform이 만들고, 태스크엔 버킷 생성 권한을 주지 않는다
- 버킷 설정(Terraform): 퍼블릭 액세스 차단, SSE-S3/SSE-KMS 기본 암호화, 수명주기 정책.

### 2-8. 메일 → SES
- SES SMTP 엔드포인트(`email-smtp.<region>.amazonaws.com:587`)를 `MAIL_HOST`에 넣으면 **코드 변경 없음**.
- 사전 작업: 도메인 인증(DKIM/SPF), **샌드박스 해제 요청**(안 하면 인증된 주소로만 발송 가능).

### 2-9. 시크릿 → Secrets Manager / SSM Parameter Store
- 옮길 것(`.docker/.env.example` 기준):
  - DB: `MEMBER_DB_PASSWORD`, `FILE_DB_PASSWORD`, `NOTIFICATION_DB_PASSWORD` (+ RDS 마스터 비밀번호)
  - `REDIS_PASSWORD`, `INTERNAL_SERVICE_TOKEN`, `STORAGE_ENCRYPTION_KEY`
  - `MAIL_PASSWORD` (SES SMTP 자격증명으로 교체)
  - `DISCORD_MESSAGING_WEBHOOK_URL`, `DISCORD_SERVICE_WEBHOOK_URL` (알림을 디스코드로 계속 보낼 경우)
- **AWS에선 필요 없는 것**: `SQS_*`, `STORAGE_S3_ACCESS_KEY`/`SECRET_KEY`(task role로 대체), `LOCALSTACK_AUTH_TOKEN`(로컬 전용).
- ECS 태스크 정의의 `secrets`로 환경변수 주입 → 앱은 지금처럼 `${...}`로 읽음.
- 비용: Parameter Store(SecureString)는 무료, Secrets Manager는 시크릿당 과금 대신 자동 로테이션.
  RDS 비밀번호만 Secrets Manager(로테이션), 나머지는 Parameter Store로 충분.
- `STORAGE_ENCRYPTION_KEY`는 장기적으로 KMS 봉투 암호화 검토.

### 2-10. ★ 모니터링 · 알림
Promtail은 docker socket 기반이라 **Fargate에서 못 쓴다** — 로그 수집은 반드시 바뀐다.

| | A. AWS 네이티브 (추천) | B. Grafana 스택 유지 |
|---|---|---|
| 로그 | CloudWatch Logs (`awslogs` 드라이버) | FireLens(Fluent Bit) → Loki(ECS, S3 백엔드) |
| 트레이스 | ADOT Collector → **X-Ray** | ADOT/otel-collector → Tempo(S3 백엔드) |
| 메트릭 | ADOT → Amazon Managed Prometheus 또는 CloudWatch | Prometheus 자체 호스팅 |
| 대시보드 | CloudWatch (필요시 Amazon Managed Grafana) | Grafana 자체 호스팅 |
| 알림 | 규칙을 CloudWatch Alarm 등으로 **다시 작성** | 현재 Grafana 알림 규칙 그대로 |
| 운영 부담 | 낮음 | Loki/Tempo/Prometheus/Grafana 직접 운영 |

- 앱은 어느 쪽이든 **OTLP 그대로** — 지금 `OTEL_EXPORTER_OTLP_ENDPOINT`(기본 `http://otel-collector:4318`)로 보내므로 collector 주소만 바꾸면 된다.
- 현재 알림: Grafana 알림 규칙 (`.docker/observability/grafana/alerting/alerts.yaml`) → 디스코드 채널 2개. 규칙 목록은 `spec/006-discord-alert-spec.md`.
  A를 고르면 이 규칙들을 옮겨야 한다.

### 2-11. 네트워크 / 도메인 / 프론트
- VPC: 퍼블릭 서브넷(ALB), 프라이빗 서브넷(ECS, RDS, ElastiCache).
- NAT Gateway는 비싸다 — S3/ECR/SQS/Secrets Manager/CloudWatch는 **VPC 엔드포인트**로 돌리면 NAT 트래픽 절감.
- Route 53(도메인) + ACM(인증서). ModuDrive-WEB은 **S3 + CloudFront** 정적 호스팅,
  `CLIENT_URL`/CORS/쿠키 도메인 재설정 필요.
- ⚠️ **WEB과 API는 같은 등록 도메인(사이트)에 있어야 한다** (예: `app.modudrive.com` / `api.modudrive.com`, 또는 CloudFront path behavior로 `/api/*`를 같은 origin에 붙임). 세션 쿠키가 `SameSite=Strict` + host-only라서 ([004 인증](spec/004-auth-spec.md) 1-1-2), 사이트가 다르면 로그인은 200인데 이후 요청에 쿠키가 안 실려 전부 401이 된다. `*.cloudfront.net`·`*.elb.amazonaws.com` 기본 도메인은 Public Suffix List에 있어 서로 다른 사이트로 취급되므로 **커스텀 도메인 필수**.

### 2-12. IaC / 배포
- 아직 없음 — 레포에 Terraform 코드와 GitHub Actions 워크플로 모두 없다.
- **Terraform**으로 전부 코드화 (콘솔 수작업 금지 — 재현·리뷰 불가).
- GitHub Actions: OIDC로 AWS 인증(장기 키 없음) → 이미지 빌드 → ECR 푸시 → ECS 서비스 업데이트.

### 2-13. ★ 서비스 간 접근 제어: 서비스별 보안 그룹 (필수)

**이관할 때 반드시 적용한다.** 지금 서비스 간 인증은 모든 서비스가 같은 `INTERNAL_SERVICE_TOKEN` 하나를 쓰고, 외부에 열린 게이트웨이도 그 값을 갖고 있다 ([004 인증](spec/004-auth-spec.md) 5장). 그래서 한 서비스가 뚫리면 비밀번호 하나로 모든 서비스의 `/internal` 경로를 부를 수 있다. 서비스별 보안 그룹은 **"누가 누구를 부를 수 있나"를 네트워크에서 강제**해서 이 피해 범위를 호출 관계 그대로 줄인다.

- 서비스(ECS 서비스)마다 보안 그룹을 **하나씩** 만든다. 여러 서비스가 보안 그룹을 공유하지 않는다.
- 인바운드는 **"허용할 호출자의 보안 그룹 → 내 앱 포트"**만 연다. CIDR(`10.0.0.0/16` 같은 대역)로 열지 않는다 — 대역으로 열면 같은 VPC 안의 모든 태스크가 닿는다.
- 공용 토큰은 **두 번째 방어선으로 그대로 둔다** — 보안 그룹 설정 실수가 있어도 한 번 더 막는다.
- 효과 예: 게이트웨이가 뚫려도 네트워크상 member·file·storage의 `/internal`에는 닿지 않는다 (게이트웨이는 그 서비스들의 **공개 API 포트**로만 라우팅하고, 그 요청은 게이트웨이 세션 확인을 거친다). 게이트웨이 전용 토큰을 따로 만들 필요가 없어진다.

#### 인바운드 규칙 (2026-09-25 코드 기준 호출 관계)

| 보안 그룹 | 허용할 출발지 | 포트 | 이유 (코드) |
|---|---|---|---|
| `alb-sg` | 인터넷 `0.0.0.0/0` | 443 | 유일한 외부 진입점 |
| `gateway-sg` | `alb-sg` | 10001 | ALB → gateway |
| `auth-sg` | `gateway-sg` | 10011 | 라우팅(`/api/v1/auth/**`) + 세션 확인(`AuthClient`) |
| `member-sg` | `gateway-sg`, `auth-sg`, `file-sg` | 10010 | 라우팅 / 로그인 확인(auth `MemberClient`) / 공유 대상 조회(file `MemberClient`) |
| `file-sg` | `gateway-sg`, `storage-sg` | 10012 | 라우팅 / 버전·zip 항목 조회·업로드 완료(storage `FileServiceFeignClient`) |
| `storage-sg` | `gateway-sg`, `file-sg` | 10013 | 라우팅 / 파일 삭제(file `StorageServiceClient`) |
| `notification-sg` | `gateway-sg` | 10015 | 라우팅(`/api/v1/notifications/**`) |
| `mail-sg` | **없음** | — | HTTP API가 없다 (SQS 소비만). 게이트웨이도 라우팅하지 않는다 |

데이터 저장소도 같은 원칙으로 **쓰는 서비스만** 연다.

| 보안 그룹 | 허용할 출발지 | 포트 |
|---|---|---|
| `rds-sg` | `member-sg`, `file-sg`, `notification-sg` (각자 자기 DB 로그인만 가능 — 2-4) | 5432 |
| `redis-sg` | `auth-sg`, `member-sg`, `storage-sg`, `mail-sg` | 6379 |

- 관리 포트(9464, actuator·Prometheus)는 모니터링 수집기(ADOT 또는 Prometheus)의 보안 그룹에서만 연다 (2-10 결정 후).
- 아웃바운드: SQS·S3·Secrets Manager·ECR·CloudWatch는 VPC 엔드포인트로 가고(2-11), SES(메일)와 외부 알림(디스코드)만 NAT로 나간다. 아웃바운드도 좁히려면 엔드포인트용 보안 그룹과 NAT 경로만 허용한다.
- ECS Service Connect를 써도 그대로 적용된다 — 호출은 호출하는 태스크의 네트워크 인터페이스에서 출발하므로, 받는 쪽 보안 그룹에서 출발지 보안 그룹으로 걸러진다.

#### 유지 규칙

- **새 Feign/WebClient 호출을 추가하면 이 표와 Terraform 규칙을 같이 고친다.** 안 고치면 AWS에서만 타임아웃이 난다 (로컬 compose는 전부 열려 있어 문제가 안 보인다).
- 표는 `@FeignClient`·`clients.<서비스>.url` 목록과 맞아야 한다: `grep -rn "@FeignClient\|clients\." services/*/src/main`.

#### 이후 단계 (선택)

보안 그룹은 **네트워크 수준**이라 "허용된 서비스가 허용되지 않은 경로를 부르는 것"은 못 막는다 (예: file이 member의 로그인 확인 API 호출). 경로 단위 권한과 비밀번호 제거가 필요해지면 **VPC Lattice + IAM 인증**으로 옮긴다 — 각 태스크 역할로 SigV4 서명, Lattice 정책으로 "gateway 역할만 auth의 세션 확인 허용" 같은 규칙, 공용 토큰 제거(로컬만 유지). 서비스별 요금과 Service Connect 설계 변경이 따르므로 이관이 안정된 뒤 검토한다.

---

## 3. 진행 순서와 현황

1. **이식성 작업** (로컬에서 검증 가능한 것들) — ✅ 완료
   - 서비스별 DB 분리 — #355 / PR #358
   - Flyway 스키마 관리 — #359 / PR #360
   - 서비스 레지스트리 제거 → 고정 URL 호출 — #363 / PR #366
   - `S3Config` task role 대응 — #364 / PR #367
   - SQS 어댑터 — #365 / PR #368
   - 로컬 SQS·S3를 LocalStack으로 통일 — #403 / PR #404
2. ⬜ **Terraform 기반 인프라** — VPC, RDS, ElastiCache, S3, SQS, SES, ECR, ECS, ALB, Secrets, **서비스별 보안 그룹 (2-13, 필수)**
3. ⬜ **CI/CD** — GitHub Actions → ECR → ECS
4. ⬜ **모니터링 · 알림** — 2-10 결정 후
5. ⬜ **WEB** — S3 + CloudFront, 도메인 연결

---

## 4. 결정 현황

| 항목 | 결정 |
|---|---|
| 메시징 | **SQS** (2026-09-19 확정) |
| 컴퓨팅 | **ECS Fargate** (2026-09-19 확정) |
| 서비스 간 접근 제어 | **서비스별 보안 그룹 + 공용 토큰 유지** (2026-09-25 확정, 2-13). VPC Lattice + IAM은 이관 안정화 후 검토 |
| ★ 모니터링 · 알림 | 미정 — AWS 네이티브(추천) vs Grafana 스택 자체 호스팅 |
