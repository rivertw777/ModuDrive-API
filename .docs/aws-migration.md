# AWS 구성

로컬 docker compose 구성이 AWS에서 무엇으로 바뀌는지, 그리고 AWS 구성을 `demo`·`prod` 두 규모로 어떻게 나눴는지 정리한 문서입니다.
코드가 기준이다. AWS 쪽은 `.infra/`, 로컬 쪽은 `.docker/`를 같이 보면서 읽는다.

---

## 목차

- [1. 로컬과 AWS](#1-로컬과-aws)
  - [1-1. 한눈에 보기](#1-1-한눈에-보기)
  - [1-2. 컴퓨팅](#1-2-컴퓨팅)
  - [1-3. 서비스 간 호출](#1-3-서비스-간-호출)
  - [1-4. 진입점](#1-4-진입점)
  - [1-5. DB](#1-5-db)
  - [1-6. Redis](#1-6-redis)
  - [1-7. 메시징](#1-7-메시징)
  - [1-8. 파일 저장](#1-8-파일-저장)
  - [1-9. 메일](#1-9-메일)
  - [1-10. 시크릿](#1-10-시크릿)
  - [1-11. 네트워크와 도메인](#1-11-네트워크와-도메인)
  - [1-12. 서비스 간 접근 제어](#1-12-서비스-간-접근-제어)
  - [1-13. 모니터링과 알림](#1-13-모니터링과-알림)
  - [1-14. Terraform과 배포](#1-14-terraform과-배포)
- [2. demo, staging, prod](#2-demo-staging-prod)
  - [2-1. 한눈에 보기](#2-1-한눈에-보기)
  - [2-2. demo](#2-2-demo)
  - [2-3. staging](#2-3-staging)
  - [2-4. prod: 가용성과 비용](#2-4-prod-가용성과-비용)
  - [2-5. prod: 저장 데이터 암호화](#2-5-prod-저장-데이터-암호화)
  - [2-6. prod: 엣지 필터링과 서비스 간 TLS](#2-6-prod-엣지-필터링과-서비스-간-tls)
  - [2-7. prod: 감사와 위협 탐지](#2-7-prod-감사와-위협-탐지)
  - [2-8. prod: 백업과 복구](#2-8-prod-백업과-복구)
  - [2-9. 크기와 엔진을 바꿀 때](#2-9-크기와-엔진을-바꿀-때)

---

## 1. 로컬과 AWS

### 1-1. 한눈에 보기

| | 로컬 | AWS |
|---|---|---|
| 실행 | docker compose, 이미지 로컬 빌드 | ECS Fargate, 이미지는 ECR |
| 서비스 간 호출 | compose DNS (`http://member-service:10010`) | ECS Service Connect (같은 이름·포트) |
| 진입점 | gateway `localhost:10001` | ALB(HTTPS) → gateway |
| DB | Postgres 18 컨테이너 1개, 서비스별 DB·로그인 | RDS 또는 Aurora PostgreSQL |
| Redis | Redis 7 컨테이너 1개 | ElastiCache 또는 MemoryDB for Valkey (TLS) |
| 메시징 | LocalStack SQS | SQS 표준 큐 |
| 파일 저장 | LocalStack S3 | S3 |
| 메일 | LocalStack SES → Mailpit(`localhost:8025`) | SES |
| 시크릿 | `.docker/.env` | SSM Parameter Store, RDS 관리자 비밀번호만 Secrets Manager |
| 모니터링 | Prometheus·Promtail·Loki·otel-collector·Tempo·Grafana | AMP·CloudWatch Logs·X-Ray·Managed Grafana(prod), 중앙 ADOT collector |
| 알림 | Grafana → Discord | AMP 알림 규칙·CloudWatch 경보·Budgets → SNS → Lambda → Discord |
| 인프라 정의 | `.docker/*.yml`, `.docker/localstack/init-aws.sh` | `.infra/`, 환경마다 AWS 계정 하나 |
| 배포 | `make service` | GitHub Actions (OIDC) → ECR → ECS 새 리비전 |

앱 코드는 두 환경에서 같다. 달라지는 건 환경 변수와 인증 방식뿐이다 — 로컬은 endpoint·access key를 넣어 LocalStack을 쓰고, AWS는 둘 다 비워 두면 SDK 기본 체인으로 task role을 쓴다.

### 1-2. 컴퓨팅

- 서비스 1개가 ECS 서비스 1개다. 이미지는 로컬과 같은 `.docker/Dockerfile`로 만들어 ECR에 올린다(서비스마다 저장소, 최근 30개만 보관).
- EKS가 아니라 ECS를 쓴 이유는 운영 부담이다. 컨트롤 플레인 비용·업그레이드가 없고 서비스 7개 규모엔 충분하다.
- 배포가 실패하면 ECS 배포 서킷 브레이커가 이전 태스크 정의로 되돌린다. 오토스케일은 CPU 60% 목표 추적이다(최소·최대는 환경마다, 2장).
- 프로필은 `SPRING_PROFILES_ACTIVE=prod`다. `dev`는 테스트 계정 시드(`db/seed`)와 Swagger를 켜므로 AWS에서 쓰지 않는다.
- 컨테이너 강화는 비용이 없어서 모든 환경이 같다.
  - 이미지는 JRE(`eclipse-temurin:25-jre-alpine`)이고 비루트 사용자(`app`)로 실행한다.
  - ECS 태스크는 루트 파일시스템 읽기 전용, Linux capability 전부 제거(`drop = ["ALL"]`). 쓰기는 `/tmp` 볼륨만 된다(Tomcat multipart 임시 파일·JVM).
  - Fargate는 이름만 있는 볼륨을 root 소유 `0755`로 마운트한다. 그대로면 비루트 사용자가 `/tmp`에 못 써서 Tomcat이 뜨지 않는다. 이미지가 `/tmp`를 `VOLUME`으로 선언하고 `1777`로 두면 Fargate가 그 권한을 볼륨에 복사한다.
  - 로컬에서 같은 조건(`--read-only -v /tmp --cap-drop ALL --security-opt no-new-privileges`)으로 띄워 확인할 수 있다. `--tmpfs /tmp`는 누구나 쓸 수 있는 tmpfs라 위 권한 문제를 가리므로 쓰지 않는다.

### 1-3. 서비스 간 호출

- 서비스 레지스트리가 없다. 호출하는 쪽은 `application.yml`의 `clients.<서비스>.url`만 읽는다.
  - Feign: `@FeignClient(name = ..., url = "${clients.<서비스>.url}")`
  - gateway: `RouteConfig`(라우트)와 `WebClientConfig`(auth 세션 확인)가 같은 값을 읽는다.
- 로컬은 compose DNS, AWS는 Service Connect가 같은 이름·포트(`http://member-service:10010`)를 붙이므로 설정 값이 바뀌지 않는다.
- Service Connect에 이름을 등록하는 건 누군가 부르는 서비스(member·auth·file·storage·notification)와 collector(`otel-collector`)다. gateway와 mail은 클라이언트로만 들어간다 — 트레이스를 collector로 보내야 해서 mail도 프록시 사이드카가 붙는다.

### 1-4. 진입점

- 퍼블릭 ALB(TLS 1.2 이상, ACM 인증서) → gateway. 나머지 서비스는 전부 프라이빗 서브넷이다.
- AWS API Gateway로 바꾸지 않는다. 세션 인증 필터·CSRF 가드·라우팅을 다 옮겨야 하는데 얻는 게 없다.
- ALB는 `drop_invalid_header_fields`로 이름이 `[A-Za-z0-9-]`가 아닌 헤더를 버린다. 요청 밀반입을 막고, 클라이언트가 보낸 `X_USER_ID`가 gateway까지 오지 못하게 한다.
- ALB 헬스 체크는 gateway 관리 포트(9464)로 한다.
- ALB → gateway 구간은 VPC 안의 평문 HTTP다.

### 1-5. DB

- 서비스마다 DB와 로그인이 따로다: `member_db`/`member_service`, `file_db`/`file_service`, `notification_db`/`notification_service`, `auth_db`/`auth_service`. 각 로그인은 자기 DB에만 접속한다.
- 로컬은 Postgres 볼륨이 비어 있을 때 `.docker/postgres/postgres_init.sh`가 만든다. RDS는 프라이빗이라 Terraform이 직접 못 만들어서, 같은 스크립트를 인스턴스마다 일회성 ECS 태스크(`modudrive-db-init[-<인스턴스>]`)로 한 번 돌린다. 명령은 출력 `db_init_run_tasks`에 있고, 태스크마다 `DB_SERVICES`로 그 인스턴스에 둘 DB만 만든다(로컬은 비워 둬서 4개 전부).
- RDS 관리자는 superuser가 아니다. PostgreSQL 16부터 `CREATE DATABASE ... OWNER <로그인>`이 `must be able to SET ROLE`로 실패하므로, 스크립트가 만든 로그인을 관리자에게 `GRANT`한다. 로컬 superuser에서도 그대로 동작한다.
- 테이블은 서비스가 뜰 때 Flyway가 만든다([db-migration.md](db-migration.md)).
- JDBC URL에 `sslmode=require`를 붙인다.
- 어느 DB를 어느 인스턴스에 둘지, 엔진(RDS·Aurora)과 크기는 환경이 정한다(2장).

### 1-6. Redis

- Valkey는 Redis 호환이라 앱은 그대로다. AWS에선 전송 암호화(TLS)를 켜고, 켜면 TLS 연결만 받는다.
  - 앱은 `application-redis.yml`의 `ssl.enabled`가 `REDIS_SSL_ENABLED`(기본 false)를 읽는다. ECS 태스크 정의에 `REDIS_SSL_ENABLED=true`만 넣는다.
  - 인증서가 Amazon 발급이라 JVM 기본 trust store로 검증된다(SSL bundle 불필요).
  - `REDIS_PASSWORD`는 AUTH 토큰이다. AUTH는 TLS가 켜져 있어야 쓸 수 있다.
- 모든 클러스터가 `maxmemory-policy noeviction`이다. 세션·인증 토큰이 메모리 압박으로 조용히 사라지지 않고, 가득 차면 쓰기가 실패한다.
- 클러스터마다 보안 그룹과 AUTH 토큰이 따로다. 그 클러스터를 쓰는 서비스만 들어오고 그 토큰만 받는다.
- 로컬처럼 하나로 둘지, 용도별(`auth`·`member`·`mail`·`storage`)로 나눌지는 환경이 정한다(2장, [006 2-4-6](spec/006-resilience-spec.md#2-4-6-redis-분리)).

### 1-7. 메시징

- SQS 표준 큐. 로컬 `.docker/localstack/init-aws.sh`와 `.infra/sqs.tf`가 같은 큐를 만든다 — 큐를 추가하면 둘 다 고친다.
- 큐마다 `-dlq`가 있고 visibility 10초, `maxReceiveCount` 4(1번 + 재시도 3번)다.
- task role은 서비스마다 보내는 큐에 `SendMessage`, 받는 큐에 수신·삭제와 자기 DLQ로의 `SendMessage`만 받는다.
- SQS를 고른 이유(Kafka·RabbitMQ 비교)와 큐 목록은 [005 메시징](spec/005-messaging-spec.md) 1장·7장.

### 1-8. 파일 저장

- `S3Config`가 환경에 따라 다르게 동작한다.
  - endpoint가 있으면(로컬 LocalStack) `endpointOverride` + path-style, 기동 시 버킷이 없으면 만든다.
  - access key가 없으면 SDK 기본 체인으로 task role을 쓴다.
- AWS 버킷(`modudrive-storage-<계정>`)은 Terraform이 만든다. storage-service task role은 객체 읽기·쓰기·삭제만 받고 버킷 생성 권한은 없다.
- 퍼블릭 액세스는 막고, HTTPS가 아닌 요청은 버킷 정책으로 거절한다. 블록은 앱이 이미 암호화해서 올리고, 버킷이 그 위에 한 번 더 암호화한다(키는 환경마다, 2-5).

### 1-9. 메일

- SMTP가 아니라 SES API(`spring-cloud-aws-starter-ses`, `SendRawEmail`)로 보낸다. 로컬은 LocalStack SES가 받은 메일을 Mailpit으로 넘기고, AWS는 `SES_ENDPOINT`를 비우면 실제 SES로 간다. 인증은 task role(`ses:SendRawEmail`)이라 관리할 SMTP 비밀번호가 없다.
- 로컬과 AWS 모두 같은 것이 있어야 한다. 하나라도 빠지면 서비스가 안 뜨거나 메일이 전부 DLQ로 간다.
  - Configuration Set `mail-events` — 없으면 발송이 400(`ConfigurationSetDoesNotExist`)으로 실패한다.
  - 이벤트 대상 `SEND` → SNS 토픽 `mail-ses-events` → SQS `mail-ses-events`(raw message delivery). 리스너는 SNS envelope 없는 SES 이벤트 JSON을 읽는다. 큐가 없으면 mail-service가 기동하지 않는다.
  - `mail-ses-events` 큐 정책은 `sqs:SendMessage`를 SNS 서비스 + `aws:SourceArn` = 그 토픽으로만 허용한다. 누군가 위조한 Send 이벤트로 메일을 "보냄" 처리시키지 못하게 한다.
- 도메인이 있어야 하는 것: SES 도메인 identity, DKIM, MAIL FROM 레코드. `domain_name`이 비어 있으면 만들지 않는다(1-11).
- AWS 계정에서 따로 할 것: SES 샌드박스 해제 요청. 안 하면 인증된 주소로만 보낼 수 있다.
- 실제 SES에 처음 보낼 때 `deliveryId` 태그(`<queue>_<outboxId>`)가 거절되지 않는지 본다. LocalStack은 태그 값을 검증하지 않는다.

### 1-10. 시크릿

| 값 | 로컬 | AWS |
|---|---|---|
| 서비스별 DB 비밀번호 | `.env` `*_DB_PASSWORD` | SSM SecureString (`random_password`) |
| DB 관리자 비밀번호 | `.env` `POSTGRES_PASSWORD` | RDS가 Secrets Manager에서 관리 |
| `REDIS_PASSWORD` | `.env` | SSM, 클러스터마다 |
| `STORAGE_ENCRYPTION_KEY` | `.env` | SSM (`random_bytes`) |
| Discord 웹후크 2개 | `.env` | SSM |
| `SQS_*`, `SES_ENDPOINT`, `STORAGE_S3_*`, `LOCALSTACK_AUTH_TOKEN` | `.env` | 없음 (task role, 실제 endpoint) |

- ECS 태스크 정의의 `secrets`로 환경 변수에 넣는다. 값은 태스크가 뜰 때 SSM에서 읽고 태스크 정의에는 남지 않는다. 앱은 로컬처럼 `${...}`로 읽는다.
- 나머지를 Secrets Manager가 아니라 Parameter Store에 두는 건 비용 때문이다. SecureString은 무료이고, 로테이션이 필요한 건 RDS 관리자 비밀번호뿐이다.
- `random_password`로 만든 값은 Terraform 상태 파일에 평문으로 들어간다. 상태 버킷은 암호화하고 접근을 배포 역할로만 제한한다.

### 1-11. 네트워크와 도메인

- VPC는 퍼블릭 서브넷(ALB, NAT), 프라이빗 서브넷(ECS 태스크), DB 서브넷(RDS, Redis)으로 나뉜다. AZ마다 하나씩이다.
- 태스크는 어느 환경이든 프라이빗 서브넷에 있고 공인 IP가 없다. 바깥(ECR·SSM·SQS·SES·Discord)으로는 NAT를 거친다. NAT의 종류와 VPC 엔드포인트 유무는 환경이 정한다(2장). S3 게이트웨이 엔드포인트는 무료라 항상 둔다.
- 도메인은 `domain_name` 변수다. 비워 두면(기본 null) Route 53 영역, ACM 인증서, HTTPS 리스너, SES identity를 만들지 않는다. 넣고 apply한 뒤 출력 `name_servers`를 도메인 등록 업체에 설정한다.
- WEB과 API는 같은 등록 도메인(사이트)에 있어야 한다. 예: `app.modudrive.com`과 `api.modudrive.com`. 세션 쿠키가 `SameSite=Strict` + host-only라서([004 인증](spec/004-auth-spec.md) 1-1-2), 사이트가 다르면 로그인은 200인데 이후 요청에 쿠키가 실리지 않아 전부 401이 된다. `*.cloudfront.net`·`*.elb.amazonaws.com`은 Public Suffix List에 있어 서로 다른 사이트로 취급되므로 커스텀 도메인이 필수다. 도메인이 없는 동안 `CLIENT_URL`은 `https://app.example.com` 자리 표시자다.
- ModuDrive-WEB은 S3 + CloudFront로 올린다(Terraform에 아직 없음). CSP는 CloudFront 응답 헤더 정책으로 붙이고, 정책 문자열은 WEB `vite.config.ts`의 `contentSecurityPolicy()`를 원본으로 한다. `connect-src`에 API 도메인을 넣고 둘을 같이 고친다.

### 1-12. 서비스 간 접근 제어

서비스 간 호출(`/internal/**`)에는 앱 수준 인증이 없다. gateway가 이 경로를 바깥에 열지 않는 것에만 기댄다([004 인증](spec/004-auth-spec.md)). 로컬 compose 네트워크는 전부 열려 있지만 AWS에선 서비스마다 보안 그룹을 하나씩 두고, "누가 누구를 부를 수 있나"를 네트워크에서 강제한다.

- 인바운드는 호출하는 쪽 보안 그룹 → 내 앱 포트만 연다. CIDR로 열지 않는다. 대역으로 열면 같은 VPC의 모든 태스크가 닿는다.
- 예를 들어 gateway가 뚫려도 member·file·storage의 `/internal`에는 닿지 않는다. gateway는 그 서비스들의 공개 API로만 라우팅하고, 그 요청은 세션 확인을 거친다.
- Service Connect를 써도 그대로 적용된다. 호출은 호출하는 태스크의 네트워크 인터페이스에서 출발하므로 받는 쪽 보안 그룹이 출발지 보안 그룹으로 거른다.

`.infra/security_groups.tf`와 같은 내용이다.

| 보안 그룹 | 허용할 출발지 | 포트 | 이유 |
|---|---|---|---|
| `alb-sg` | 인터넷 `0.0.0.0/0` | 443 | 유일한 외부 진입점 |
| `gateway-sg` | `alb-sg` | 10001, 9464 | ALB → gateway, 헬스 체크 |
| `auth-sg` | `gateway-sg` | 10011 | 라우팅(`/api/v1/auth/**`) + 세션 확인(`AuthClient`) |
| `member-sg` | `gateway-sg`, `auth-sg`, `file-sg` | 10010 | 라우팅 / 로그인 확인(auth `MemberClient`) / 공유 대상 조회(file `MemberClient`) |
| `file-sg` | `gateway-sg`, `storage-sg` | 10012 | 라우팅 / 버전·zip 항목·커밋된 블록 조회(storage `FileClient`) |
| `storage-sg` | `gateway-sg`, `file-sg` | 10013 | 라우팅 / commit 때 올라온 블록 조회(file `StorageClient`). 블록 삭제는 SQS |
| `notification-sg` | `gateway-sg` | 10015 | 라우팅(`/api/v1/notifications/**`) |
| `mail-sg` | 없음 | — | HTTP API가 없다(SQS 소비만) |
| `rds-sg` (인스턴스마다) | 그 인스턴스에 DB가 있는 서비스, `db-init-sg` | 5432 | |
| `redis-sg` (클러스터마다) | 그 클러스터를 쓰는 서비스 | 6379 | |
| `otel-collector-sg` | 모든 서비스 | 4318 | 트레이스(OTLP). 반대로 collector는 모든 서비스의 9464를 수집한다 |

- 새 Feign·WebClient 호출을 추가하면 이 표와 Terraform 규칙을 같이 고친다. 안 고치면 로컬에선 멀쩡하고 AWS에서만 타임아웃이 난다. 확인은 `grep -rn "@FeignClient\|clients\." services/*/src/main`.
- 관리 포트(9464)는 중앙 ADOT collector에만 연다(1-13, gateway만 예외로 ALB에도).
- 보안 그룹은 네트워크 수준이라 허용된 서비스가 허용되지 않은 경로를 부르는 건 못 막는다(예: file이 member의 로그인 확인 API 호출). 경로 단위 권한이 필요해지면 VPC Lattice + IAM 인증(태스크 역할로 SigV4 서명)을 검토한다. 서비스별 요금과 Service Connect 설계 변경이 따른다.

### 1-13. 모니터링과 알림

로컬 스택은 [observability.md](observability.md). AWS에선 저장소를 전부 관리형으로 두고, 직접 띄우는 건 중앙 ADOT collector ECS 서비스 하나뿐이다(`monitoring.tf`). 지금 만든 것(PromQL 알림, tail sampling, OTLP)을 그대로 쓰는 게 기준이다.

```
ECS 태스크들 ──OTLP 트레이스──▶ ┌─────────────────────┐ ──▶ X-Ray
            ◀──:9464 수집─────  │ 중앙 ADOT collector │ ──▶ AMP ──(알림 규칙)──┐
            ──stdout(awslogs)─▶ └─────────────────────┘                        ▼
                 CloudWatch Logs        CloudWatch 경보·Budgets·GuardDuty ──▶ SNS ──▶ Lambda ──▶ Discord
```

| 신호 | 로컬 | AWS | 수집 |
|---|---|---|---|
| 메트릭 | Prometheus | Amazon Managed Prometheus (AMP) | collector의 `ecs_observer`가 태스크를 찾아 `:9464` 수집 → remote write |
| 트레이스 | otel-collector → Tempo | X-Ray | 앱 OTLP → collector(tail sampling) → X-Ray |
| 로그 | Promtail → Loki | CloudWatch Logs (보존 14일) | `awslogs` 드라이버. Promtail은 docker socket 기반이라 Fargate에서 못 쓴다 |
| 알림 | Grafana 알림 → Discord | AMP 알림 규칙 → SNS → Lambda → Discord | |
| 대시보드 | Grafana | Amazon Managed Grafana (`grafana = true`일 때) | AMP·X-Ray·CloudWatch 데이터소스 |

앱 설정은 바뀌지 않는다. collector의 Service Connect 이름이 compose와 같은 `otel-collector:4318`이라 앱 기본값(`application-observability.yml`)이 그대로 맞는다. 그래서 mail도 Service Connect에 클라이언트로 들어간다.

collector:

- 사이드카가 아니라 1개다. 한 trace의 span이 전부 한 collector로 모여야 tail sampling(에러 + 1초 초과 + 5%)이 로컬처럼 동작한다. 태스크마다 사이드카를 붙이면 span이 흩어져 "에러 trace 전량 보관"이 깨진다. collector가 죽어도 telemetry만 잠시 빠지고 서비스는 영향이 없다(앱은 비동기 export, 실패하면 버린다). 2대 이상으로 늘릴 땐 앞단 collector가 `loadbalancing` exporter로 traceID 기준 라우팅하는 2단 구성으로 바꾼다.
- 로컬 `prometheus.yml`은 서비스 7개를 `static_configs`로 박아 두지만 ECS에선 태스크 IP가 바뀌고 서비스당 태스크가 여럿이다. `ecs_observer`가 `ECS_PROMETHEUS_EXPORTER_PORT` 도커 라벨(`ecs.tf`)이 붙은 컨테이너를 찾고, 컨테이너 이름을 `service` 라벨로 붙인다. 알림은 `instance`(태스크 IP) 대신 이 라벨로 서비스를 가리킨다.
- AMP는 샘플 수 과금이라 알림과 기본 대시보드에 쓰는 지표만 남기고 버린다(`otel-collector.yaml`의 `metric_relabel_configs`). 새 지표로 알림을 만들려면 여기에 먼저 넣는다. 수집 주기는 `otel_collector.scrape_interval`(demo·staging 60초, prod 30초).
- ADOT 이미지의 사용자(`aoc`)는 쓸 수 있는 디렉터리가 없다. `ecs_observer`가 대상 목록 파일을 써야 해서 uid 0으로 띄우되, capability를 전부 빼고 루트 파일시스템을 읽기 전용으로 둬 `/tmp` 볼륨 말고는 아무것도 못 쓰게 한다.
- 메트릭 레이블에 userId·fileId를 넣지 않는다. 고유값은 span 태그와 로그에만 둔다. 그래서 AMP는 고객 관리 키 없이 AWS 소유 키로 둔다.

알림:

- 로컬 `alerts.yaml`의 규칙을 Prometheus 형식으로 옮겼다(`monitoring/alert-rules.yaml`). 쿼리·기준값·대기 시간은 같다. 바뀐 건 서비스 라벨, 확인 명령어(`docker logs` → `aws logs tail`, TraceQL → X-Ray 필터), 그리고 규칙 하나가 늘었다 — ECS에선 죽은 태스크가 `up = 0`이 아니라 대상 목록에서 사라지므로 `ServiceNoTasks`가 `absent(up{service=...})`로 그걸 본다.
- 묶음(`group_by: [alertname]`)·재알림(4시간)·채널 두 개(messaging·service)도 로컬과 같다(`monitoring/alertmanager.yaml`). Discord 문구는 로컬 템플릿에서 Grafana 링크만 뺐다.
- Managed Grafana의 알림이 아니라 AMP 알림 규칙을 쓰는 이유: Managed Grafana의 규칙은 로컬처럼 파일로 둘 수 없고, Terraform grafana provider로 관리하려면 최대 30일짜리 서비스 계정 토큰이 필요하다. AMP 규칙은 Terraform 리소스 하나라 리뷰·재현이 된다. 대신 AMP 알림은 SNS로만 나가므로 Discord로 넘기는 Lambda(`monitoring/discord_forwarder.py`)를 둔다. Lambda는 VPC 밖에 있어 NAT가 멈춰도 알림은 간다.
- AWS 쪽 경보(CloudWatch)도 같은 SNS 토픽으로 보낸다. 앱 지표로는 안 보이는 것들이다.

| 경보 | 기준 | 앱 알림으로 안 보이는 이유 |
|---|---|---|
| `queue-age-<큐>` | 가장 오래된 메시지가 5분 넘게 대기 | 컨슈머가 느리거나 멈추면 실패 없이 쌓이기만 해서 DLQ 알림이 안 울린다 |
| `alb-5xx` | ALB가 직접 응답한 5xx가 5분에 10건 초과 | gateway가 죽으면 gateway 지표 자체가 없다 |
| `gateway-unhealthy` | 헬스 체크를 통과한 gateway가 0개 | 위와 같다 |
| `nat-instance-status` | NAT 인스턴스 상태 검사 실패 (demo·staging) | 바깥 통신만 멈추고 서비스는 떠 있다 |
| Budgets | 실제 비용 80% 초과, 월말 예상 100% 초과 | `monthly_budget_usd`, `alert_email`을 넣으면 메일로도 간다 |
| GuardDuty | 심각도 7 이상 (prod·staging) | 2-7 |

- Discord 웹후크 URL은 SSM에 손으로 한 번 넣는다(`aws ssm put-parameter --name /modudrive/DISCORD_SERVICE_WEBHOOK_URL --type SecureString --overwrite --value ...`, messaging도 같이). Lambda가 읽는다.
- 로그 검색은 LogQL 대신 CloudWatch Logs Insights다. JSON 필드라 `filter traceId = "..."`, `filter userId = "..."`가 바로 된다.
- ALB는 W3C `traceparent`가 아니라 `X-Amzn-Trace-Id`를 붙인다. trace는 gateway부터 시작하고, ALB 구간은 ALB 접근 로그로 본다.

다른 선택지를 뺀 이유:

| 선택지 | 뺀 이유 |
|---|---|
| CloudWatch만 | 알림을 CloudWatch Alarm으로 다시 써야 한다. Prometheus 지표를 커스텀 메트릭으로 넣으면 레이블 조합마다 과금이라 히스토그램 버킷 × URI × 상태 × 태스크로 비용이 커진다 |
| Grafana 스택 직접 운영 | 저장소(S3/EFS)·업그레이드·장애 대응을 혼자 떠안는다 |
| Grafana Cloud | 운영은 가장 쉽지만 데이터가 AWS 밖으로 나가 전송 비용이 들고, 양이 늘면 요금이 빠르게 오른다 |

비용:

- 로그가 비용의 대부분이다. 보존 14일로 두고 INFO 로그를 줄이는 것부터 한다. 그래도 크면 FireLens(Fluent Bit)로 두 갈래로 나눈다 — ERROR/WARN·감사 로그는 CloudWatch Logs, 전량은 S3(Athena 조회).
- X-Ray는 trace 건수 과금이라 tail sampling이 그대로 비용을 정한다.
- demo에서 늘어나는 건 월 $13 안팎이다: collector Spot 태스크 ~$4, AMP ~$8(지표 거르기 + 60초 수집), 경보 ~$1. X-Ray·Lambda·Budgets는 이 규모에선 무료 구간이다.

처음 apply한 뒤 확인할 것:

- `ecs_observer`가 대상을 찾는지 — collector 로그에 `getDiscoverableTasks` 오류가 없고, AMP에서 `up{job="services"}`가 서비스마다 보이는지. `service` 라벨이 비어 있으면 relabel의 `__meta_ecs_container_name` 이름을 collector 버전 문서와 맞춘다.
- X-Ray가 앱의 W3C trace ID(첫 32비트가 타임스탬프가 아님)를 그대로 받는지. 안 되면 X-Ray ID 생성기를 설정한다.
- 알림 하나를 일부러 울려 Discord까지 오는지 — 예: notification 서비스를 0개로 줄여 `ServiceNoTasks`.
- collector 1대의 CPU·메모리, 특히 `decision_wait`(30초) 동안 쌓이는 span.

### 1-14. Terraform과 배포

환경마다 AWS 계정과 상태가 따로다. 같은 코드를 `envs/<환경>.tfvars`로 크기만 바꿔 띄운다(2장).

- 계정 분리: demo·staging·prod는 각자 다른 AWS 계정(Organizations 아래)에 있다. 리소스 이름에 환경을 붙이지 않는 건 계정이 이미 나눠 주기 때문이고, GuardDuty·Config·Security Hub 같은 계정 단위 설정도 이래야 겹치지 않는다. 백업 계정이 하나 더 있다(2-8).
- 상태: 계정마다 S3 상태 버킷이 있다. 잠금은 `use_lockfile`(S3 자체 잠금, DynamoDB 불필요)이다. 버킷은 Terraform이 자기 자신을 관리할 수 없으니 계정마다 한 번 손으로 만든다(버전 관리 켬). 버킷 이름은 `envs/<환경>.backend.hcl`에 두고 git에는 올리지 않는다.
- 잘못된 계정 막기: tfvars의 `account_id`를 채우면 provider가 다른 계정 자격 증명을 거부한다. prod.tfvars를 demo 계정에 plan하는 실수가 여기서 멈춘다.
- `random_password`로 만든 값은 상태 파일에 평문으로 들어간다. 상태 버킷은 암호화하고 접근을 배포하는 사람의 역할로만 제한한다.

```bash
cp .infra/envs/backend.hcl.example .infra/envs/demo.backend.hcl   # 그 계정의 버킷 이름
cd .infra
terraform init -reconfigure -backend-config=envs/demo.backend.hcl
terraform plan -var-file=envs/demo.tfvars -var image_tag=<지금 배포된 커밋>
# 계정 없이 문법만: terraform init -backend=false && terraform validate
```

계정 없이 세 환경을 plan까지 돌려 볼 수 있다(`tests/plan.tftest.hcl`, provider를 흉내 낸다). tfvars의 `for_each`, 파일 사이 참조, precondition까지 걸린다.

```bash
terraform init -backend=false
for env in demo staging prod; do terraform test -var-file=envs/$env.tfvars; done
```

첫 apply는 순서가 있다. 서비스는 ECR 이미지와 DB가 있어야 뜬다. 모든 명령에 `-var-file=envs/<환경>.tfvars`를 붙인다.

1. `terraform apply -target=aws_ecr_repository.service -var image_tag=init` — 저장소만 먼저
2. 서비스 이미지 7개를 빌드해 같은 태그로 ECR에 푸시
3. `terraform apply -target=aws_ecs_task_definition.db_init -var image_tag=<태그>` → 출력 `db_init_run_tasks`의 명령을 인스턴스마다 한 번 실행
4. `terraform apply -var image_tag=<태그>` — 나머지 전부
5. GitHub 저장소 Settings에 배포가 쓸 값을 넣는다.
   - Environments → 그 환경(`demo` 등): 변수 `AWS_DEPLOY_ROLE_ARN`(출력 `github_deploy_role_arn`). 계정마다 역할이 달라서 환경마다 넣는다. prod에는 승인자(required reviewers)를 건다.
   - Environments → `demo-terraform`(demo만): 변수 `AWS_TERRAFORM_ROLE_ARN`(출력 `github_terraform_role_arn`)과 `TF_STATE_BUCKET`(상태 버킷 이름). 배포 브랜치를 `demo`로 제한한다.
   - Secrets and variables → Actions → Repository secrets: `LOCALSTACK_AUTH_TOKEN`. 배포 전 테스트 중 SQS 큐 설정 테스트가 LocalStack을 띄울 때 쓴다. AWS 환경과 무관하게 같은 값이라 저장소에 한 번만 넣는다.
   - 계정에 GitHub OIDC provider가 이미 있으면 4번이 실패하니 `terraform import aws_iam_openid_connect_provider.github <ARN>`으로 가져온다.

배포는 GitHub Actions가 한다(`.github/workflows/deploy.yml`). job이 둘이고 순서대로 돈다.

| 브랜치 | terraform job | deploy job |
|---|---|---|
| `demo` (dev를 병합) | demo에 plan → apply | demo에 앱 배포 |
| `prod` | 없음 (사람이 apply) | prod에 앱 배포 |
| 수동 실행 | demo를 고르면 위와 같음 | 고른 환경 |

- demo는 `dev`를 `demo` 브랜치로 병합(PR)하면 인프라와 코드가 같이 나간다. 새 환경 변수가 필요한 코드도 apply가 먼저라 순서가 맞는다. staging·prod는 apply를 사람이 하고, 새 환경 변수가 필요한 코드는 apply 뒤에 배포한다.
- 환경에 역할 ARN이 없으면(스택을 아직 안 띄웠으면) 두 job 모두 아무것도 하지 않고 끝난다.

terraform job:

- `terraform plan -var-file=envs/demo.tfvars -var image_tag=template` → 계획 요약을 실행 요약(Summary)에 남기고 → apply.
- 데이터를 가진 리소스(DB, Redis, S3 버킷, 큐, KMS 키, 비밀값)를 지우거나 교체하는 계획이면 apply하지 않고 실패한다. 그런 변경은 스냅샷을 뜬 뒤 사람이 한다(2-9).
- 역할은 `github-terraform`(`github.tf`)이다. Terraform이 IAM·KMS·네트워크까지 다루므로 계정 관리자 권한이고, 그래서 `terraform_in_ci = true`인 demo에만 만든다. GitHub environment `demo-terraform`만 이 역할을 받는다 — 그 environment의 배포 브랜치를 `demo`로 제한한다.
- `image_tag`가 `template`로 고정인 이유: 첫 배포 뒤로 Terraform의 태스크 정의 리비전은 deploy job이 복사하는 틀일 뿐이다. 커밋 SHA를 넣으면 push마다 태스크 정의 7개가 바뀌는 계획이 나와 진짜 변경이 묻힌다.

deploy job:

- 테스트 → 이미지 7개 빌드(태그는 커밋 SHA) → ECR 푸시 → 서비스마다 태스크 정의의 최신 리비전에서 이미지만 바꾼 새 리비전을 등록 → 서비스를 그 리비전으로 바꾼다 → 안정될 때까지 기다리고, 배포 서킷 브레이커가 롤백했으면 실패로 끝난다.
- 최신 리비전에서 출발하므로 Terraform이 바꾼 태스크 정의(환경 변수, 크기)도 이때 함께 나간다.
- 배포 역할(`github-deploy`)은 그 환경의 GitHub environment에서 도는 job만 받는다. 할 수 있는 건 이미지 푸시와 태스크 정의 등록·서비스 갱신뿐이라 배포 권한으로 DB나 네트워크를 건드릴 수 없다.
- Terraform은 서비스가 어느 리비전을 돌리는지 무시한다(`ignore_changes = [task_definition]`). 그래서 apply가 서비스를 옛 이미지로 되돌리지 않는다.
- 스키마 변경은 이전 코드와 새 코드 모두에서 동작해야 한다. 새 태스크가 뜨면서 Flyway가 돌 때 이전 태스크가 아직 요청을 받고 있다. 컬럼 삭제·이름 변경은 두 번의 배포로 나눈다([db-migration.md](db-migration.md) 5장).
- 롤백은 이전 커밋으로 워크플로를 수동 실행한다. ECR은 최근 30개 이미지를 남긴다.

---

## 2. demo, staging, prod

같은 Terraform 코드를 값 파일 하나로 세 규모로 띄운다. 서비스·보안 그룹·큐·IAM·모니터링 같은 구조는 같고, 돈이 드는 것과 이중화만 다르다. 셋은 각자 다른 AWS 계정에 있다(1-14).

- `.infra/envs/demo.tfvars` — 실제로 띄우는 시험용 스택
- `.infra/envs/staging.tfvars` — prod의 구성(Aurora, MemoryDB, 보안 강화)을 가장 작은 크기로. prod에 넣기 전에 확인하는 곳
- `.infra/envs/prod.tfvars` — MAU 500만을 목표로 한 운영 설계

크기 변수(`variables.tf`)에는 기본값이 없어서 `-var-file` 없이는 plan이 안 된다. 파일을 섞어 쓰지 않는다.

### 2-1. 한눈에 보기

| | `demo` | `staging` | `prod` |
|---|---|---|---|
| AZ | 2개 | 2개 | 3개 |
| NAT | NAT 인스턴스 1대 (t4g.nano, fck-nat) | NAT 인스턴스 1대 | AZ마다 NAT 게이트웨이 |
| VPC 인터페이스 엔드포인트 | 없음 (S3 게이트웨이만) | 없음 | SQS·ECR(api, dkr)·logs·SSM·Secrets Manager |
| ECS 용량 | Fargate Spot | Fargate Spot | Fargate |
| 태스크 크기 | 전부 0.25 vCPU / 1 GB | demo와 같음 | gateway·auth·file 1 vCPU / 2 GB, storage 2 vCPU / 4 GB, 나머지 0.5 vCPU / 1 GB |
| 태스크 수 | 서비스마다 1개 고정 | demo와 같음 | gateway·auth·file·storage 최소 3개, 나머지 최소 2개, 최대 6~30개 |
| DB | RDS 1대(db.t4g.micro, 단일 AZ)에 DB 4개 | Aurora 클러스터 1개(db.t4g.medium 1대)에 DB 4개 | 서비스마다 Aurora 클러스터 (쓰기 1 + 읽기 1) |
| DB 백업 | 7일 | 35일 | 35일 + 백업 계정으로 매일 복사 |
| Redis | ElastiCache 1개, cache.t4g.micro 1노드, 모든 서비스 공용 | MemoryDB 1개, db.t4g.small 1노드, 공용 | MemoryDB 용도별 4개, 샤드마다 노드 2 |
| 삭제 방지 | 끔 | 끔 | 켬 (RDS 최종 스냅샷 포함) |
| Container Insights | 끔 | 끔 | 켬 |
| 보안 강화 (`hardened`) | 끔 | 켬 | 켬 — KMS 고객 관리 키, S3 버전 관리, WAF, Service Connect TLS, 감사 로그 |
| Managed Grafana | 없음 | 없음 | 있음 |
| 월 예산 알림 (`monthly_budget_usd`) | $150 | $400 | $8,000 |
| 대략 비용 (서울, 트래픽 전) | 월 $120 안팎 | 월 $300 안팎 | 월 $5,800 안팎 (2-4) |

### 2-2. demo

시험용이라 줄일 수 있는 비용은 다 줄인다. 격리와 이중화는 `prod` 설계로 보여 준다.

월 $120의 내역은 대략 태스크 7개 Spot ~$26, ALB ~$22 + 공인 IPv4 2개 ~$7, RDS ~$21, ElastiCache ~$18, NAT 인스턴스 ~$8(공인 IP 포함), 모니터링 ~$13(1-13), 로그 등 ~$5다.

비용 때문에 고른 것:

- NAT 게이트웨이(AZ마다 월 ~$45) 대신 NAT 인스턴스 1대. 태스크마다 공인 IPv4를 붙이는 것(7개 ~$26)보다도 싸다.
- 인터페이스 엔드포인트 없음. 2개 AZ에 6개를 두면 월 ~$110이다. AWS API 호출은 NAT로 나간다.
- AZ 2개. 3번째 AZ는 ALB 공인 IPv4만 늘리고 시험용으로는 얻는 게 없다.
- 태스크는 JVM이 뜨는 가장 작은 크기(0.25 vCPU / 1 GB)에 하나씩, 오토스케일 없음. 트래픽이 몰리면 비용이 오르는 대신 느려진다. 기동도 느려서 헬스 체크 유예 시간을 길게 잡는다(`ecs.tf` `startPeriod`).
- DB는 인스턴스 1대에 4개를 모은다. 서비스마다 DB·로그인이 따로인 논리 분리는 `prod`와 같다.
- Redis는 로컬처럼 하나를 같이 쓴다.
- Managed Grafana 없음. 사용자당 월 $9에 IAM Identity Center 설정이 필요하다. 시험용으로는 AMP·X-Ray·CloudWatch 콘솔로 충분하다. 알림은 Grafana와 무관하게 온다.

감수하는 것:

- 바깥으로 나가는 통신이 NAT 인스턴스 1대에 걸린다. 멈추면 화면·API는 그대로지만 이벤트·메일·새 태스크 기동이 복구될 때까지 기다린다(EC2 자동 복구, `nat-instance-status` 경보).
- Spot 회수나 재배포 때 서비스마다 태스크가 하나라 잠깐 끊긴다.
- Redis가 하나라 업로드가 몰리면 세션과 메모리를 나눠 쓰고, 가득 차면(`noeviction`) 로그인도 실패한다. Redis가 재시작되면 전원 로그아웃된다.
- 어디에도 Multi-AZ가 없다. 백업도 이 계정 안에만 있다.

암호화는 AWS 관리 키(SSE-S3, SSE-SQS, `aws/ssm` 등)를 쓴다.

### 2-3. staging

prod에만 있는 것을 prod에 넣기 전에 돌려 보는 곳이다. demo로는 확인할 수 없는 것들이다.

- Aurora와 MemoryDB(클러스터 모드 — 해시 태그, 토폴로지 변경 추적)
- 고객 관리 KMS 키, S3 버전 관리(버전 관리 버킷에서 블록 정리의 조건부 삭제가 412·삭제 마커로 동작하는지, 2-5)
- WAF 오탐(2-6), Service Connect TLS를 켠 첫 배포(2-6)
- GuardDuty·Config·Security Hub의 import·첫 apply(2-7)

크기는 demo 수준이고 이중화도 없다. 클러스터를 서비스마다 나누는 건 같은 것을 더 만드는 것뿐이라 하나로 모았다(DB 4개, Redis 1개). 비용의 대부분은 보안 강화의 고정비다 — 사설 CA ~$50, WAF, Config 기록, Aurora·MemoryDB 최소 노드.

### 2-4. prod: 가용성과 비용

보안·일관성·가용성·분할 내성을 먼저 정하고, 그걸 지키는 데 필요한 만큼만 둔다. 숫자는 MAU 500만의 출발점이지 측정값이 아니다 — 부하 테스트와 오토스케일 기록으로 맞춘다.

AZ를 3개 쓰는 건 하나를 잃거나 하나와 끊겨도 과반이 남게 하기 위해서다. Aurora 저장소 쿼럼과 ECS 태스크 배치가 이걸 전제로 한다.

DB — 서비스마다 Aurora PostgreSQL 18 클러스터 1개:

- 쓰기 1대 + 읽기 1대. file은 db.r7g.xlarge, 나머지는 db.r7g.large.
- 저장소는 인스턴스 수와 상관없이 AZ 3곳에 6벌이고 4벌이 확인해야 커밋된다. AZ 하나를 잃어도 커밋된 쓰기를 잃지 않는다. 읽기 노드는 장애 조치 시간을 산다 — 있으면 승격에 30초 안팎, 없으면 인스턴스를 새로 만드는 10분 안팎이다. 서비스는 쓰기 엔드포인트로만 읽고 쓰므로(복제 지연 없이 모든 읽기가 마지막 커밋을 본다) 읽기 노드를 2대로 늘려도 얻는 게 없다.
- TLS 강제(`rds.force_ssl`), Performance Insights, 백업 35일(특정 시점 복구).
- 클러스터를 나눈 이유: file-service의 부하(업로드, outbox)가 로그인 경로를 늦추지 않고, 클러스터마다 크기·장애 조치·업그레이드를 따로 한다. 보안 그룹도 클러스터마다라서 각 서비스는 자기 클러스터에만 닿는다.

Redis — MemoryDB for Valkey, 용도별 4개(`auth`·`member`·`mail`·`storage`):

- ElastiCache는 복제가 비동기라 장애 조치 때 마지막 쓰기를 잃을 수 있다. MemoryDB는 쓰기가 Multi-AZ 트랜잭션 로그에 남은 뒤 응답하므로 장애 조치에도 잃지 않는다. 지속성은 복제본이 아니라 로그가 맡으므로 샤드마다 주 노드 1 + 복제본 1이면 된다.
- `auth`는 db.r7g.large. `member`(가입 인증 코드)와 `mail`(소비 기록)은 담는 게 적어서 db.t4g.medium. `storage`는 업로드 기록(사용자당 하루 최대 25,600키)이 많아 샤드 2개에 db.r7g.xlarge. 스냅샷 35일.
- 클러스터 모드라 한 명령에서 함께 쓰는 키에는 해시 태그를 붙인다.
- 나눈 이유: 한 용도의 부하나 메모리 부족이 다른 용도로 번지지 않는다. 클러스터별 내용은 [006 2-4-6](spec/006-resilience-spec.md#2-4-6-redis-분리).

ECS:

- 사용자 요청을 받는 gateway·auth·file·storage는 최소 3개(AZ마다 하나), 나머지는 최소 2개. CPU 60% 기준으로 늘어난다.
- Spot이 아닌 일반 Fargate다. 회수로 태스크가 빠지지 않는다.

네트워크:

- AZ마다 NAT 게이트웨이를 둬서 바깥 통신에 단일 지점이 없다.
- SQS·ECR·CloudWatch Logs·SSM·Secrets Manager는 VPC 인터페이스 엔드포인트로 간다. NAT를 거치지 않으니 NAT 처리 비용이 줄고, 바깥으로 나가는 건 SES·Discord·AMP·X-Ray 정도다.

비용 — 서울 온디맨드 시간당 단가 × 730시간, 트래픽에 따라 붙는 비용(S3 저장·요청, 데이터 전송, 로그, WAF 요청, GuardDuty 이벤트, AMP 샘플, Aurora 저장·I/O, MemoryDB 쓰기량) 전:

| 항목 | 구성 | 월 |
|---|---|---|
| Aurora | r7g.xlarge 2대 ($0.665/h) + r7g.large 6대 ($0.333/h) | ~$2,430 |
| MemoryDB | r7g.xlarge 4대 ($0.516/h) + r7g.large 2대 ($0.258/h) + t4g.medium 4대 ($0.099/h) | ~$2,170 |
| ECS Fargate | 최소 태스크 18개 + collector (vCPU $0.0466/h, GB $0.0051/h) | ~$790 |
| 네트워크 | NAT 게이트웨이 3개 ($0.059/h), 인터페이스 엔드포인트 6개 × AZ 3 ($0.013/h), ALB | ~$320 |
| 보안 고정비 | 사설 CA, WAF 규칙, KMS | ~$65 |
| 합계 | | ~$5,800 |

- 읽기 노드 2대, 복제본 2개, 모든 서비스 최소 3개로 두면 같은 기준으로 월 $8,800 안팎이다. 그만큼 더 내도 일관성·지속성은 같고 장애 조치 후보만 하나 늘어난다.
- 다음으로 줄일 곳: 1년 예약(Aurora·MemoryDB 30~40%), Fargate Savings Plans, 부하 테스트 뒤 `storage` MemoryDB 크기.

### 2-5. prod: 저장 데이터 암호화

`hardened = true`일 때 켜진다(`kms.tf`, `s3.tf`).

- KMS 고객 관리 키 하나(`alias/modudrive-data`, 매년 자동 교체)로 암호화한다: Aurora 저장소·Performance Insights, RDS가 관리하는 관리자 비밀번호, MemoryDB, S3 블록(버킷 키로 KMS 호출을 줄임), SQS 큐·DLQ, SSM 비밀값, CloudWatch 로그 그룹. AWS 관리 키와 달리 키 정책과 사용 기록(CloudTrail)을 직접 통제한다.
- RDS와 ECS가 직접 만드는 로그 그룹(Aurora 쿼리 로그, Container Insights)도 Terraform이 먼저 만들어 키를 건다. 그냥 두면 암호화 없이 무기한 보존된다.
- 키 정책은 계정(IAM 정책으로 위임), CloudWatch Logs(이 리전 로그 그룹만), SNS(`mail-ses-events` 토픽이 큐에 암호화해 넣도록)에 연다. IAM으로는 실행 역할에 SSM·Secrets Manager 복호화를, 큐를 쓰는 서비스와 storage에 데이터 키 생성·복호화만 준다.
- S3 버전 관리를 켠다. 지워지거나 덮어쓰인 블록을 30일 동안 되살릴 수 있고, 그 뒤 이전 버전은 수명 주기 규칙이 지운다.
- 적용 전 스테이징에서 확인할 것: 버전 관리 버킷에서 블록 정리의 조건부 삭제(`DeleteObject` + `If-Match`)가 그대로 412·삭제 마커로 동작하는지.

### 2-6. prod: 엣지 필터링과 서비스 간 TLS

WAF(`waf.tf`, ALB에 연결) — AWS 관리 규칙(IP 평판, 알려진 악성 입력, 공통 규칙, SQLi)과 IP별 요청 한도:

- 공통 규칙의 "본문 8KB 초과 차단"은 기록만 한다. commit 요청은 블록 해시를 전부 담아서 큰 파일이면 8KB를 넘는다. 본문 크기 제한은 앱이 맡는다.
- 블록 업로드(`/api/v1/storage/blocks`)는 공통 규칙과 SQLi 규칙에서 뺀다. 본문이 파일 바이트 그대로라 .html이나 .sql 파일을 올리면 XSS·SQLi 패턴에 걸린다.
- 알려진 악성 입력 규칙 중 본문을 보는 두 개(Log4J, Java 역직렬화)도 기록만 한다. 로그 파일이나 .ser 파일을 올리면 걸린다. 헤더·경로 검사는 그대로 막는다.
- 세션 없이 비밀번호·코드를 확인하거나 메일을 보내는 경로(`/api/v1/auth/login`, `/api/v1/auth/verify-email/*`, `/api/v1/member/sign-up`, `/api/v1/member/verify-email/*`)는 IP당 5분에 100회까지다. 앱은 계정·기기별로만 제한하므로, 한 주소에서 여러 계정을 대입하거나 메일을 쏟아내는 건 여기서 막는다. 경로는 URL 디코딩과 정규화 뒤 비교해서 `//`나 `%2F`로 비켜 갈 수 없다.
- 전체 요청은 IP당 5분에 20,000회까지다. 업로드가 8MB 블록마다 요청을 하나씩 보내고, 회사망이나 통신사 NAT 뒤에는 사용자가 여럿 모이므로 넉넉히 잡았다.
- WAF 로그는 `aws-waf-logs-modudrive` 그룹에 30일 남기고, 세션 쿠키 헤더는 가린다.
- 운영 첫 1~2주는 WAF 로그의 `terminatingRuleId`로 오탐을 본다. 파일·폴더 이름이나 검색어가 XSS·LFI·SQLi 규칙에 걸리면 그 규칙만 기록 전용으로 돌린다. 통신사 CGNAT 뒤에서 로그인 한도에 걸리는지도 `login-rate` 지표로 본다.

Service Connect TLS(`service_connect_tls.tf`):

- 단기 인증서 전용 사설 CA(ACM PCA)와 ECS 인프라 역할을 둔다. member·auth·file·storage·notification의 Envoy 프록시가 TLS로 받고, 인증서 발급과 교체는 ECS가 한다.
- 앱은 여전히 로컬 프록시와 평문 HTTP로 통신하므로 코드는 그대로다.
- 켜고 처음 배포할 때 서버 쪽과 클라이언트 쪽 서비스가 새 설정으로 함께 바뀐다. 롤링 중에 평문과 TLS 연결이 섞이지 않는지 스테이징에서 먼저 본다.

남은 평문 구간은 ALB → gateway다. 바꾸려면 gateway에 인증서를 두고 대상 그룹을 HTTPS로 바꿔야 한다.

### 2-7. prod: 감사와 위협 탐지

`audit.tf`. 기록은 로그 버킷(`modudrive-logs-<계정>`) 하나에 모은다.

- 버킷은 TLS만 받고, 90일 뒤 Glacier Instant Retrieval로 옮겨 1년 뒤 지운다.
- Object Lock(GOVERNANCE, 365일)을 건다. 버킷 쓰기 권한을 얻은 침입자도 1년 안의 기록을 지우거나 덮어쓰지 못한다. Object Lock은 버킷을 만들 때만 켤 수 있다.
- ALB 접근 로그는 KMS 버킷에 쓰지 못해서 버킷 기본 암호화는 SSE-S3이고, CloudTrail 파일만 KMS 키로 암호화한다.

| 무엇 | 남기는 것 | 비고 |
|---|---|---|
| CloudTrail | 계정의 모든 API 호출(전 리전), storage 버킷의 쓰기·삭제 | 로그 파일 검증(다이제스트)으로 사후 변조를 확인할 수 있다. 블록 읽기는 다운로드마다 생겨 양이 많고 앱 로그에 이미 남으므로 뺀다 |
| VPC Flow Logs | VPC 안 모든 연결(허용·거부) | S3로 바로 보낸다 |
| ALB 접근 로그 | 요청마다 클라이언트 IP·상태·지연 | WAF에 막혀 gateway까지 못 온 요청도 남는다 |
| GuardDuty | CloudTrail·Flow·DNS 이상 징후, S3 데이터 이벤트, RDS 로그인, Fargate 런타임 | S3 보호는 블록 읽기까지 전부 분석하고 이벤트 수만큼 과금해서 GuardDuty 비용 대부분이 여기서 나온다. 블록은 파일 조각이라 S3 악성코드 검사는 켜지 않는다 |
| Security Hub | AWS 기본 보안 모범 사례, CIS 3.0 | 검사 대부분이 AWS Config 기록을 쓰므로 Config 레코더도 켠다. Fargate 태스크마다 바뀌는 ENI는 하루 한 번만 기록한다. S3 계정 퍼블릭 차단, IAM Access Analyzer, EBS 기본 암호화도 함께 켠다(무료) |

apply할 때 주의할 것:

- Config 서비스 연결 역할(`AWSServiceRoleForConfig`)이 계정에 이미 있으면 생성이 실패한다. `terraform import`로 가져온다. GuardDuty와 Security Hub가 이미 켜져 있어도 마찬가지다.
- Fargate 런타임 모니터링을 켜면 GuardDuty가 VPC에 `guardduty-data` 엔드포인트와 `GuardDutyManaged*` 보안 그룹을 직접 만든다(Terraform 밖). `terraform destroy`나 VPC 교체 전에 이 둘을 먼저 지운다. 에이전트는 기능을 켠 뒤 새로 뜨는 태스크에만 붙으므로 첫 apply 후 서비스를 재배포한다.
- apply 후 GuardDuty 콘솔에서 RDS 보호가 Aurora PostgreSQL 18을 실제로 다루는지 본다. 다시 `plan`을 돌려 런타임 모니터링 설정에 diff가 남지 않는지도 본다.

한계: CloudTrail은 전 리전을 기록하지만 GuardDuty·Config·Security Hub는 서울만 본다. 다른 리전에 몰래 만든 리소스는 기록에는 남지만 탐지되지 않는다. 메우려면 리전마다 provider alias를 두거나 Organizations 위임 관리자를 쓴다.

GuardDuty 발견 중 심각도 7 이상은 EventBridge → SNS로 Discord에 온다(1-13). Security Hub 발견은 양이 많아 알리지 않고 콘솔의 점수와 실패 항목으로 본다.

### 2-8. prod: 백업과 복구

`backup.tf`. prod의 기본 백업(Aurora 35일, S3 이전 버전 30일, MemoryDB 스냅샷 35일)은 전부 데이터와 같은 계정·리전에 있다. 계정을 탈취당하거나 리전 전체가 멈추면 백업도 같이 잃는다. 그래서 별도 백업 계정으로 사본을 보낸다.

| 무엇 | 어떻게 | 백업 계정 보관 |
|---|---|---|
| Aurora (서비스별 DB) | AWS Backup 매일 05:00 스냅샷 → 백업 계정 볼트로 복사 (`backup_copy_vault_arn`) | 90일 (이 계정 볼트 7일) |
| storage 블록 (S3) | S3 복제 → 백업 계정 버킷, Glacier Instant Retrieval (`backup_bucket_arn`) | 백업 계정의 수명 주기 규칙 |
| `STORAGE_ENCRYPTION_KEY` | 손으로 한 번 복사 (아래) | 영구 |
| MemoryDB | 복사하지 않는다 | — |

- 삭제는 복제하지 않는다. 앱의 블록 정리든 버그든 침입자든, 여기서 지운 블록은 사본에 남는다. 사본을 언제 지울지는 백업 계정의 수명 주기 규칙이 정한다.
- 블록 사본은 앱이 이미 암호화한 바이트라 `STORAGE_ENCRYPTION_KEY` 없이는 읽을 수 없다. 키를 잃으면 원본도 사본도 쓸모가 없으므로 키도 백업 계정에 둔다. 키는 바뀌지 않으므로(`prevent_destroy`) 한 번이면 된다.

  ```bash
  aws ssm get-parameter --name /modudrive/STORAGE_ENCRYPTION_KEY --with-decryption \
    --query Parameter.Value --output text --profile prod \
  | aws secretsmanager create-secret --name modudrive-prod/STORAGE_ENCRYPTION_KEY \
    --secret-string file:///dev/stdin --profile backup
  ```

- MemoryDB는 AWS Backup이 지원하지 않는다. 담긴 것(세션, 인증 코드, 업로드 기록, 다운로드 한도)은 다시 만들어지는 값이라 잃으면 전원 다시 로그인하고 올리던 블록을 다시 보내면 된다.

백업 계정에 먼저 만들 것 (Terraform 밖, 백업 계정 쪽):

- Organizations 관리 계정에서 AWS Backup 계정 간 백업을 켠다.
- 고객 관리 KMS 키로 암호화한 백업 볼트, 그리고 prod 계정이 `backup:CopyIntoBackupVault`를 할 수 있게 하는 볼트 접근 정책.
- 블록 버킷: 버전 관리 켬, 고객 관리 KMS 키로 기본 암호화, Object Lock(선택). 버킷 정책으로 prod 계정의 `modudrive-s3-replication` 역할에 `s3:ReplicateObject`·`s3:ReplicateTags`·`s3:ObjectOwnerOverrideToBucketOwner`를, KMS 키 정책으로 같은 역할에 `kms:Encrypt`·`kms:GenerateDataKey`를 연다.
- 이 셋의 ARN을 prod.tfvars의 `backup_*` 값에 넣는다. 비어 있으면 위 리소스는 만들어지지 않는다.
- prod의 데이터 키는 백업 계정이 스냅샷을 다시 암호화할 수 있게 키 정책에 백업 계정이 들어간다(`kms.tf`).

복구 목표:

| 상황 | 데이터 손실 (RPO) | 복구 시간 (RTO) | 방법 |
|---|---|---|---|
| AZ 하나 장애 | 없음 | Aurora 30초 안팎, MemoryDB 수십 초 | 자동 장애 조치 |
| 잘못된 쓰기·삭제 (DB) | 5분 이내 | 30분~1시간 | Aurora 특정 시점 복구로 옆에 새 클러스터를 만들고, 필요한 행을 원래 클러스터로 옮긴다 |
| 잘못된 삭제 (블록) | 없음 (30일 안) | 수 분 | S3 이전 버전 복원 |
| 계정 탈취·리전 장애 | DB 24시간, 블록 수 분 | 수 시간 | 새 계정에 Terraform으로 스택 → 백업 계정의 스냅샷 복원 → 버킷 사본을 새 버킷으로 복사, 키 복원 |

계정·리전 복구 절차는 연습해 본 적이 없다. 숫자는 목표이고, staging에서 한 번 돌려 보고 고친다.

### 2-9. 크기와 엔진을 바꿀 때

- ECS 서비스(Spot ↔ 일반)와 서브넷이 교체되고, DB 클래스 변경은 재시작이 따른다. 사용자가 없을 때 바꾼다.
- `db_engine`을 바꾸면(`rds` ↔ `aurora`) 기존 DB가 삭제되고 새 DB가 빈 채로 생긴다. 스냅샷 복원이나 덤프로 데이터를 옮긴 뒤 바꾼다. 클러스터 하나를 서비스별로 나눌 때도 같다 — 공용 인스턴스는 `file` 이름이라 file_db는 그 자리에 남지만, member·auth·notification DB는 새 클러스터로 옮겨야 한다.
- `redis_engine`을 바꾸면(`elasticache` ↔ `memorydb`) 클러스터가 전부 새로 생긴다. 세션이 사라져 전원 로그아웃되고, 이메일 인증 토큰·메일 소비 기록·업로드 기록·다운로드 한도·zip 토큰도 사라진다. 업로드 중이던 블록은 다시 보내면 된다.
- MemoryDB 파라미터 그룹(`memorydb_valkey7`, 엔진 7.3)과 ElastiCache 파라미터 그룹(`valkey8`, 엔진 8.1)은 엔진 버전이 다르다. 기존 클러스터에 적용하기 전에 엔진 버전을 먼저 확인한다.
