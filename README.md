## 1. 서비스 개요

<br>

모두드라이브는 파일을 안전하게 보관하고, 다른 사람과 공유할 수 있는 **클라우드 스토리지 서비스**입니다.

파일을 4MB 블록의 해시 목록으로 표현해 변경된 블록만 전송하고, 블록은 Presigned URL로 S3에 직접 올려 서버를 거치지 않는 **블록 기반 업로드**를 구현해 대용량 파일도 빠르고 효율적으로 올릴 수 있게 하였습니다.

또한 비동기 메시징과 서킷 브레이커 기반 장애 격리를 적용해, 일부 서비스나 외부 시스템에 장애가 발생해도 안정적으로 서비스할 수 있는 시스템을 구축하였습니다.

<br>

<div align="center">
<table align="center">
  <tr><th>서비스 링크</th></tr>
  <tr><td align="center"><a href="https://app.modudrive.help">https://app.modudrive.help</a></td></tr>
</table>
</div>

<br>

---

## 2. 프로젝트 목표

<br>

- **효율적인 업로드**: 블록 해시 기반 업로드로 중복 전송을 없애고, 끊긴 업로드를 이어 올리기

- **정합성**: Transactional Outbox로 서비스 간 비동기 통신의 정합성 보장

- **장애 격리**: 서킷 브레이커·재시도·시간 제한으로 외부 API 장애가 서비스 전체로 번지지 않게 하기

- **관측성**: 게이트웨이부터 메시지 소비까지 이어지는 분산 트레이싱 구축

- **모니터링**: 서버 장애 시 Discord로 알림을 보내는 모니터링 시스템 구축

- **IaC**: Terraform으로 AWS 인프라를 코드로 구성하고, GitHub Actions로 배포 자동화

<br>

---

## 3. 시스템 아키텍처

<br>

<p align="center">
  <img src=".docs/images/architecture.jpeg" width="75%" alt="시스템 아키텍처">
</p>

<div align="center">
<table align="center">
  <tr><th>서비스</th><th>역할</th></tr>
  <tr><td align="center">gateway-service</td><td align="center">Spring Cloud Gateway, 세션 인증·CSRF 검사</td></tr>
  <tr><td align="center">auth-service</td><td align="center">로그인, Redis 서버 세션, 새 기기 이메일 인증</td></tr>
  <tr><td align="center">member-service</td><td align="center">회원가입, 회원 조회, 비밀번호 검증</td></tr>
  <tr><td align="center">file-service</td><td align="center">파일 메타데이터, 버전, 공유, 폴더 관리</td></tr>
  <tr><td align="center">storage-service</td><td align="center">블록 업로드 URL 발급, 블록 확인, S3 다운로드</td></tr>
  <tr><td align="center">mail-service</td><td align="center">메일 발송 (SQS 소비)</td></tr>
  <tr><td align="center">notification-service</td><td align="center">인앱 알림 (SQS 소비)</td></tr>
</table>
</div>

<br>

---

## 4. 기술적 의사결정

<br>

- [블록 기반 업로드](https://github.com/rivertw777/ModuDrive-API/wiki/%EB%B8%94%EB%A1%9D-%EA%B8%B0%EB%B0%98-%EC%97%85%EB%A1%9C%EB%93%9C)
- [파일 메타데이터와 스토리지의 정합성 보장](https://github.com/rivertw777/ModuDrive-API/wiki/%ED%8C%8C%EC%9D%BC-%EB%A9%94%ED%83%80%EB%8D%B0%EC%9D%B4%ED%84%B0%EC%99%80-%EC%8A%A4%ED%86%A0%EB%A6%AC%EC%A7%80%EC%9D%98-%EC%A0%95%ED%95%A9%EC%84%B1-%EB%B3%B4%EC%9E%A5)
- [분산 트레이싱 도입](https://github.com/rivertw777/ModuDrive-API/wiki/%EB%B6%84%EC%82%B0-%ED%8A%B8%EB%A0%88%EC%9D%B4%EC%8B%B1-%EB%8F%84%EC%9E%85)

<br>

---

## 5. 트러블슈팅

<br>

- [폴더 삭제 기능 성능 개선](https://github.com/rivertw777/ModuDrive-API/wiki/%ED%8F%B4%EB%8D%94-%EC%82%AD%EC%A0%9C-%EA%B8%B0%EB%8A%A5-%EC%84%B1%EB%8A%A5-%EA%B0%9C%EC%84%A0)
- [외부 API 장애 대응하기](https://github.com/rivertw777/ModuDrive-API/wiki/%EC%99%B8%EB%B6%80-API-%EC%9E%A5%EC%95%A0-%EB%8C%80%EC%9D%91%ED%95%98%EA%B8%B0)

<br>

---

## 6. 설계 문서

<br>

- [파일 업로드 스펙](./.docs/spec/001-file-upload-spec.md)
- [파일 다운로드 스펙](./.docs/spec/002-file-download-spec.md)
- [파일/폴더 공유 기능 스펙](./.docs/spec/003-file-sharing-spec.md)
- [인증 스펙](./.docs/spec/004-auth-spec.md)
- [비동기 메시징 스펙](./.docs/spec/005-messaging-spec.md)
- [장애 대응 (Resilience) 스펙](./.docs/spec/006-resilience-spec.md)
- [디스코드 알림 스펙](./.docs/spec/007-discord-alert-spec.md)

<br>

---

## 7. 구성 가이드

<br>

- [DB 마이그레이션 (Flyway)](./.docs/guide/001-db-migration.md)
- [모니터링](./.docs/guide/002-observability.md)
- [AWS 마이그레이션](./.docs/guide/003-aws-migration.md)

<br>

---

## 8. 기술 스택

<br>

<div align="center">
<table align="center">
    <tr>
        <th align="center">카테고리</th>
        <th align="center">기술/도구</th>
    </tr>
    <tr>
        <td align="center"><strong>Backend</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/Java%2025-007396?logo=openjdk&logoColor=white"/> <img src="https://img.shields.io/badge/Spring%20Boot%204.1-6DB33F?logo=springboot&logoColor=white"/> <img src="https://img.shields.io/badge/Spring%20Cloud%202025.1-6DB33F?logo=spring&logoColor=white"/> </td>
    </tr>
    <tr>
        <td align="center"><strong>Database</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/PostgreSQL-316192?logo=postgresql&logoColor=white"/> </td>
    </tr>
    <tr>
        <td align="center"><strong>Cache</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/Redis-DD0031?logo=redis&logoColor=white"/> </td>
    </tr>
    <tr>
        <td align="center"><strong>Messaging</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/Amazon%20SQS-FF4F8B?logo=amazonsqs&logoColor=white"/> </td>
    </tr>
    <tr>
        <td align="center"><strong>Storage</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/Amazon%20S3-569A31?logo=amazons3&logoColor=white"/> </td>
    </tr>
    <tr>
        <td align="center"><strong>Mail</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/Amazon%20SES-DD344C?logo=amazonsimpleemailservice&logoColor=white"/> </td>
    </tr>
    <tr>
        <td align="center"><strong>Container</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/Amazon%20ECS-FF9900?logo=amazonecs&logoColor=white"/> <img src="https://img.shields.io/badge/Docker-0db7ed?logo=docker&logoColor=white"/> </td>
    </tr>
    <tr>
        <td align="center"><strong>IaC · CI/CD</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/Terraform-7B42BC?logo=terraform&logoColor=white"/> <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?logo=githubactions&logoColor=white"/> </td>
    </tr>
    <tr>
        <td align="center"><strong>Monitoring</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/OpenTelemetry-425CC7?logo=opentelemetry&logoColor=white"/> <img src="https://img.shields.io/badge/Prometheus-E6522C?logo=prometheus&logoColor=white"/> <img src="https://img.shields.io/badge/Grafana-F46800?logo=grafana&logoColor=white"/> </td>
    </tr>
    <tr>
        <td align="center"><strong>Alert</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/Discord-5865F2?logo=discord&logoColor=white"/> </td>
    </tr>
    <tr>
        <td align="center"><strong>Test</strong></td>
        <td align="center"> <img src="https://img.shields.io/badge/JUnit5-25A162?logo=junit5&logoColor=white"/> <img src="https://img.shields.io/badge/Testcontainers-291A3F"/> </td>
    </tr>
</table>
</div>
