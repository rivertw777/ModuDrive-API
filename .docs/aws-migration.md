# AWS 이관 기술 매핑

로컬 docker compose 인프라를 AWS 관리형 서비스로 옮길 때 무엇으로 대체할지 정리한 문서입니다.
2026-09-19 기준 코드 조사 결과. **확정**: 메시징 SQS, 컴퓨팅 ECS Fargate (2026-09-19). `★`는 아직 결정이 필요한 항목.

---

## 1. 한눈에 보기

| 현재 (로컬) | AWS 대체 | 앱 변경 범위 |
|---|---|---|
| docker compose + 이미지 로컬 빌드 | **ECS Fargate** + **ECR** | 없음 (Dockerfile 그대로) |
| Eureka (서비스 디스커버리) | **ECS Service Connect** (Cloud Map) | 중간 — `lb://`, `@FeignClient(name=)` 해석 방식 변경 |
| gateway-service (Spring Cloud Gateway) | 그대로 유지 + 앞단 **ALB** | 라우트 URI만 |
| Postgres 18 | **RDS for PostgreSQL** | 없음 (접속 정보만) |
| Redis 7 | **ElastiCache for Valkey** | 설정 (TLS on) |
| Kafka (단일 브로커) | **SQS 표준 큐** (확정, #365) — 로컬은 LocalStack (#403) | 완료 — PR #368 |
| MinIO → LocalStack (#403) | **S3** | 작음 — `S3Config` 수정 |
| SMTP (MAIL_HOST) | **SES** (SMTP 인터페이스) | 없음 (설정만) |
| `.env` 시크릿 | **Secrets Manager** / SSM Parameter Store | 없음 (ECS가 env로 주입) |
| Prometheus/Promtail/Loki/Tempo/Grafana | ★ **CloudWatch + X-Ray (ADOT)** 또는 Grafana 스택 자체 호스팅 | 없음 (OTLP 유지) |
| (없음) | **Route 53 + ACM**, **CloudFront** (WEB 정적 호스팅) | — |
| (없음) | **Terraform** (IaC), **GitHub Actions OIDC** (배포) | — |

---

## 2. 항목별 상세

### 2-1. 컴퓨팅: ECS Fargate (확정)
- EKS 대비 운영 부담이 훨씬 적고(컨트롤 플레인 비용·업그레이드 없음), 서비스 8개 규모엔 충분.
- 서비스 1개 = ECS Service 1개. 이미지는 ECR, 기존 `.docker/Dockerfile` 재사용.
- 오토스케일: ECS Service Auto Scaling(CPU/요청 수 기준).
- 액추에이터 관리 포트(9464)는 보안그룹으로 내부에서만 열기 — 지금 구조(퍼블리시 안 함)와 동일한 원칙.

### 2-2. 서비스 디스커버리: Eureka 제거 → ECS Service Connect
- Fargate는 태스크 IP가 계속 바뀌고, Eureka 서버를 따로 띄워 관리할 이유가 없어진다.
- Service Connect를 켜면 `http://member-service:10010` 같은 **고정 DNS 이름 + 내장 로드밸런싱**이 생긴다.
- 코드 영향:
  - gateway `RouteConfig`의 `lb://member-service` → `http://member-service:10010` (설정값으로 뺄 것)
  - Feign 5곳(`@FeignClient(name = "member-service")` 등) → `url = "${clients.member.url}"` 추가
  - `eureka-client` 의존성 제거 (`common:infrastructure:spring-cloud`), eureka-server 모듈 삭제
- 로컬도 compose DNS로 같은 URL이 되므로 **로컬/운영 설정이 오히려 단순해진다**.

### 2-3. 진입점: ALB → gateway-service
- API Gateway(AWS)로 바꾸면 인증 필터·CSRF 가드·라우팅을 다 옮겨야 해서 이득이 없음. **SCG 유지**.
- 퍼블릭 ALB(HTTPS, ACM 인증서) → gateway 태스크만 퍼블릭 타깃. 나머지 서비스는 프라이빗 서브넷.

### 2-4. DB: RDS for PostgreSQL
- 처음엔 단일 인스턴스(db.t4g 계열) + 자동 백업. 트래픽 늘면 Multi-AZ → Aurora 검토.

### 2-5. Redis → ElastiCache for Valkey
- Valkey는 Redis 호환 + 더 저렴. 용도가 토큰/인증코드/다운로드 쿼터라 Serverless도 후보.
- 전송 암호화(TLS) 켜면 `spring.data.redis.ssl.enabled: true` 추가.

### 2-6. Kafka → SQS (확정)
현재 큐 4개는 **전부 구독 서비스가 1개씩**이다(`.docs/spec/messaging-spec.md`). fan-out이 없어서 SNS 없이 SQS만으로 충분.

| | SQS (선택) | MSK (기각) |
|---|---|---|
| 코드 변경 | 컨슈머 3개 + outbox relay + `common:infrastructure:kafka` 교체 (Spring Cloud AWS) | 없음 (bootstrap 주소 + IAM 인증 설정) |
| 비용 (현재 규모) | 사실상 0 (월 100만 요청 무료) | 트래픽 없어도 브로커 비용 상시 발생 |
| 순서 보장 | 없음 — 순서가 필요한 큐가 없어 표준 큐를 쓴다 | 파티션 키 그대로 |
| 재시도/DLT | redrive policy + DLQ (지금 `<topic>-dlt` 대응) | 지금 구조 그대로 |
| replay | 없음 | 가능 |
| 로컬 개발 | LocalStack (#403, 무료 비상업 auth token 필요. 그 전엔 ElasticMQ) | 지금 Kafka 그대로 |

- 나중에 fan-out이 생기면 SNS 토픽 → SQS 큐들로 확장.
- replay: 실패분은 DLQ redrive로 충분. 성공분 replay가 필요해지면 outbox 행 보관(`sent_at` + N일) + 재발행으로 해결 (`.docs/spec/messaging-spec.md` 6장).
- Terraform: 큐 4개 + `-dlq`(표준), visibility 10s, maxReceiveCount 4 — `.docker/localstack/init-aws.sh`와 동일하게.
- outbox 패턴(#350)은 그대로 유지 — relay의 전송 대상만 바뀐다.

### 2-7. MinIO → S3
- `S3Config` 수정 필요:
  - `endpointOverride`, `forcePathStyle`은 endpoint가 설정된 경우(로컬 LocalStack)에만 적용
  - 키가 없으면 `DefaultCredentialsProvider` 사용 → ECS **Task Role**로 인증 (액세스 키 제거)
  - 기동 시 `createBucket`은 로컬 전용으로 — 운영 버킷은 Terraform이 만들고 태스크엔 버킷 생성 권한을 주지 않음
- 버킷: 퍼블릭 액세스 차단, SSE-S3/SSE-KMS 기본 암호화, 수명주기 정책.
- 로컬은 LocalStack S3 (#403, 그 전엔 MinIO).

### 2-8. 메일 → SES
- SES SMTP 엔드포인트(`email-smtp.<region>.amazonaws.com:587`)를 `MAIL_HOST`에 넣으면 **코드 변경 없음**.
- 사전 작업: 도메인 인증(DKIM/SPF), **샌드박스 해제 요청**(안 하면 인증된 주소로만 발송 가능).

### 2-9. 시크릿 → Secrets Manager / SSM Parameter Store
- 대상: DB/Redis 비밀번호, `JWT_SECRET_KEY`, `INTERNAL_SERVICE_TOKEN`, `STORAGE_ENCRYPTION_KEY`, SES SMTP 자격증명.
- ECS 태스크 정의의 `secrets`로 환경변수 주입 → 앱은 지금처럼 `${...}`로 읽음.
- 비용: Parameter Store(SecureString)는 무료, Secrets Manager는 시크릿당 과금 대신 자동 로테이션.
  RDS 비밀번호만 Secrets Manager(로테이션), 나머지는 Parameter Store로 충분.
- `STORAGE_ENCRYPTION_KEY`는 장기적으로 KMS 봉투 암호화 검토.

### 2-10. ★ 모니터링
Promtail은 docker socket 기반이라 **Fargate에서 못 쓴다** — 로그 수집은 반드시 바뀐다.

| | A. AWS 네이티브 (추천) | B. Grafana 스택 유지 |
|---|---|---|
| 로그 | CloudWatch Logs (`awslogs` 드라이버) | FireLens(Fluent Bit) → Loki(ECS, S3 백엔드) |
| 트레이스 | ADOT Collector → **X-Ray** | ADOT/otel-collector → Tempo(S3 백엔드) |
| 메트릭 | ADOT → Amazon Managed Prometheus 또는 CloudWatch | Prometheus 자체 호스팅 |
| 대시보드 | CloudWatch (필요시 Amazon Managed Grafana) | Grafana 자체 호스팅 |
| 운영 부담 | 낮음 | Loki/Tempo/Prometheus 직접 운영 |

- 어느 쪽이든 앱은 **OTLP 그대로** — collector만 ADOT로 바꾸면 된다. tail sampling 정책도 ADOT에서 유지 가능.

### 2-11. 네트워크 / 도메인 / 프론트
- VPC: 퍼블릭 서브넷(ALB), 프라이빗 서브넷(ECS, RDS, ElastiCache).
- NAT Gateway는 비싸다 — S3/ECR/SQS/Secrets Manager/CloudWatch는 **VPC 엔드포인트**로 돌리면 NAT 트래픽 절감.
- Route 53(도메인) + ACM(인증서). ModuDrive-WEB은 **S3 + CloudFront** 정적 호스팅,
  `CLIENT_URL`/CORS/쿠키 도메인 재설정 필요.

### 2-12. IaC / 배포
- **Terraform**으로 전부 코드화 (콘솔 수작업 금지 — 재현·리뷰 불가).
- GitHub Actions: OIDC로 AWS 인증(장기 키 없음) → 이미지 빌드 → ECR 푸시 → ECS 서비스 업데이트.

---

## 3. 추천 진행 순서

1. **이식성 작업** (로컬에서 검증 가능한 것들)
   - Eureka 제거 → 고정 URL 기반 호출 (Service Connect 대비) — #363 / PR #366 (완료)
   - `S3Config` IAM Role 대응 — #364 / PR #367 (완료)
   - Kafka → SQS: 어댑터 교체 + 로컬 ElasticMQ (Kafka 컨테이너 제거, 이후 #403에서 LocalStack으로) — #365 / PR #368 (완료)
2. **Terraform 기반 인프라** — VPC, RDS, ElastiCache, S3, SES, ECR, ECS, ALB, Secrets
3. **CI/CD** — GitHub Actions → ECR → ECS
4. **모니터링** — ADOT + CloudWatch/X-Ray
5. **WEB** — S3 + CloudFront, 도메인 연결

---

## 4. 결정 현황
| 항목 | 결정 |
|---|---|
| 메시징 | **SQS** (2026-09-19 확정) |
| 컴퓨팅 | **ECS Fargate** (2026-09-19 확정) |
| ★ 모니터링 | 미정 — AWS 네이티브(추천) vs Grafana 스택 자체 호스팅 |
