# 파일 업로드 성능 테스트

WEB과 같은 순서로 파일을 올리면서([001 2](../spec/001-file-upload-spec.md#2-파일-업로드)) 단계마다 걸린 시간과 DB·Redis 호출 수를 잰 테스트다. 큰 파일, 작은 파일, 여러 종류가 섞인 폴더를 혼자 올릴 때와 10명이 동시에 올릴 때로 나눠 잰다. 마지막에는 배포한 서비스를 구글 드라이브·네이버 MYBOX와 같은 파일로 비교한다.

## 1. 테스트 시나리오

| # | 시나리오 | 하는 일 |
|---|---|---|
| 1 | 큰 파일 | 5GiB 파일 1개 (4MiB 블록 1,280개 — 한 파일의 최대) |
| 2 | 작은 파일 | 10KiB 파일 1,000개 (파일마다 블록 1개, commit은 100개씩 10번) |
| 3 | 혼합 폴더 | 업무 폴더 하나 (파일 552개, 1.28GiB) — 아래 표 |
| 4 | 동시 · 큰 파일 | 10명이 동시에 각자 1 |
| 5 | 동시 · 작은 파일 | 10명이 동시에 각자 2 |
| 6 | 동시 · 혼합 폴더 | 10명이 동시에 각자 3 |

혼합 폴더는 종류마다 실제에 가까운 크기로, 하위 폴더 깊이를 섞었다. 크기는 범위 안에서 고르게 뽑되 매 라운드 같고 내용만 바뀐다. 확장자는 이름에만 쓰이고(`FileCategory`) 업로드 비용은 크기와 폴더 구조로 정해진다. WEB처럼 묶음 하나에 파일 100개·블록 1,280개까지, 블록 64개(256MiB)를 넘는 파일은 혼자 한 묶음으로 나눠, 묶음 7개·블록 779개가 된다.

| 하위 폴더 | 종류 | 파일 수 | 크기 |
|---|---|---|---|
| `문서` | pdf / docx | 60 / 50 | 100KiB~5MiB / 20KiB~2MiB |
| `문서/보고서` | xlsx / hwp | 40 / 20 | 10KiB~1MiB / 30KiB~3MiB |
| `코드/src/main` | java | 250 | 1~20KiB |
| `코드` | json | 30 | 1~50KiB |
| `사진` | jpg | 60 | 2~6MiB |
| `사진/스크린샷` | png | 20 | 100KiB~3MiB |
| `음악` | mp3 | 15 | 3~10MiB |
| `압축` | zip | 5 | 10~60MiB |
| `영상` | mp4 | 2 | 120MiB, 400MiB |

한 번 올릴 때 WEB처럼 네 단계를 차례로 밟고, 단계마다 시간·DB 쿼리 수(pg_stat_statements)·Redis 왕복 수(`INFO commandstats`)를 잰다.

| 단계 | 하는 일 |
|---|---|
| commit1 | 처음 commit — 없는 블록이 `needBlocks`로 돌아온다 |
| presign | 업로드 URL 요청 (64개씩) |
| put | S3에 블록 PUT (동시 6개) |
| commit2 | 2차 commit — S3에서 블록을 확인하고 버전을 만든다 |

- 라운드마다 새 드라이브에 올리고, 끝나면 그 드라이브의 S3 블록을 지운다. 서비스를 띄운 뒤 워밍업 1번, 시나리오마다 5라운드(4는 한 라운드가 50GiB라 3라운드), 표는 중앙값이다.
- 동시 업로드는 10명이 barrier로 같이 시작한다. DB·Redis 수는 10명 합계이고, 단계 시간은 사람마다 잰 값의 중앙값과 가장 늦은 사람(max)이다. 전체 시간은 마지막 사람이 끝날 때까지다.
- 블록 내용은 시드로 그때그때 다시 만든다(5GiB를 메모리에 들고 있지 않는다).
- 서비스는 compose 네트워크 안에서 `X_USER_ID` 헤더로 file-service(10012)와 storage-service(10013)를 직접 호출한다.
- Redis 왕복은 `EVALSHA`·`ZADD`·`ZMSCORE` 같은 명령 수다. `upload-count.lua`가 안에서 실행하는 `INCR`·`PEXPIRE`는 왕복이 아니라 빼고 센다.
- 1차 commit에서 오류(`needBlocks`가 아닌 실패)가 난 파일이 있으면 그 라운드는 실패로 멈춘다. 블록을 보내지 않은 채 2차 commit까지 가면 원인이 가려진다.

레포 루트에서, 인프라와 서비스가 떠 있는 상태로 실행한다.

```bash
.scripts/perf-test/scenario.sh up                        # pg_stat_statements + bench 컨테이너
.scripts/perf-test/001-upload/run.sh image tobe          # 작업 트리로 file·storage-service 이미지를 만들어 tobe로 태그
.scripts/perf-test/001-upload/run.sh deploy tobe         # 그 이미지로 두 서비스를 다시 띄움
.scripts/perf-test/001-upload/run.sh run-1-big 5 big 1280
.scripts/perf-test/001-upload/run.sh run-2-small 5 small 1000 10240
.scripts/perf-test/001-upload/run.sh run-3-mixed 5 mixed
.scripts/perf-test/001-upload/run.sh run-4-cbig 3 concurrent 10 big 1280
.scripts/perf-test/001-upload/run.sh run-5-csmall 5 concurrent 10 small 1000 10240
.scripts/perf-test/001-upload/run.sh run-6-cmixed 5 concurrent 10 mixed
python3 summary.py run-1-big run-2-small run-3-mixed run-4-cbig run-5-csmall run-6-cmixed   # 001-upload/에서
.scripts/perf-test/scenario.sh cleanup                   # 측정용 드라이브 삭제
```

스크립트는 `.scripts/perf-test/001-upload/`, 결과 원본(라운드마다 JSON 한 줄)은 그 아래 `results/`에 있다.

## 2. 로컬 결과

2026-10-23, 로컬(WSL2, LocalStack).

**혼자 올릴 때**

| # | 시나리오 | commit1 | presign | put | commit2 |
|---|---|---|---|---|---|
| 1 | 5GiB 1개 | 54ms, DB 4번 | 227ms, Redis 54번 | 22.7초 | 198ms, DB 1,295번 |
| 2 | 10KiB × 1,000개 | 166ms, DB 40번 | 114ms, Redis 47번 | 1.6초 | 1.3초, DB 5,101번 |
| 3 | 혼합 폴더 | 110ms, DB 36번 | 68ms, Redis 41번 | 5.0초 | 794ms, DB 3,117번 |

**10명 동시** (단계 시간은 중앙값 / 가장 늦은 사람)

| # | 시나리오 | 전체 (마지막 사람까지) | commit1 | presign | put | commit2 |
|---|---|---|---|---|---|---|
| 4 | 5GiB 1개씩 | 168.4초 | 154 / 178ms | 496 / 617ms | 166.6 / 167.6초 | 0.25 / 0.72초 |
| 5 | 10KiB × 1,000개씩 | 24.3초 | 383 / 407ms | 234 / 257ms | 15.2 / 15.6초 | 8.0 / 8.4초 |
| 6 | 혼합 폴더씩 | 48.0초 | 252 / 268ms | 267 / 333ms | 42.8 / 45.4초 | 2.2 / 2.5초 |

- 혼자 올릴 때는 세 경우 모두 블록 전송(put)이 가장 길고, 2차 commit은 1.3초 이하다.
- 5의 2차 commit은 혼자일 때(1.3초)보다 6배 길다. S3 확인은 목록으로 줄었으니, 남은 것은 10명이 file-service 하나와 DB를 나눠 쓰는 비용이다. 무엇이 먼저 차는지(DB 커넥션 풀, CPU)는 재지 않았다.
- DB 쿼리 수는 pg_stat_statements가 JDBC 배치 안의 행을 하나씩 센 값이다. 실제 왕복은 이보다 적다.
- put은 같은 PC 안에서 LocalStack으로 보낸 시간이라, 브라우저에서 S3로 올리는 실제 시간과 다르다([4](#4-배포구글-드라이브네이버-mybox-비교)).

## 3. HEAD 동시 요청 수

블록 확인을 목록으로 바꾸기 전, HEAD로 확인하던 때의 측정이다. 해시가 64개 미만이면 지금도 HEAD를 쓴다.

commit 하나가 블록 조회에 동시에 보내는 S3 호출 수(`S3StorageAdapter.LOOKUP_PARALLELISM`)는 8개다. #681에서 32개로 늘렸다가 #683에서 8개로 되돌렸다. 로컬 동시 업로드에서 32개가 HEAD 자리(40개, 최대 1초 대기)를 기다리는 줄을 길게 만들어, 자리를 못 받은 조회가 503 → 재시도로 오히려 느려졌다(10명 동시 16.8초 → 24.4초). 실제 S3에서 32개가 빠른 경우는 5GiB 파일 하나를 혼자 올릴 때 0.5초뿐이었다.

AWS S3에서 잰 값이다.

- demo ECS 클러스터에 일회성 Fargate 작업(2 vCPU, 4GB)을 띄웠다. storage-service와 같은 서브넷·보안 그룹·작업 역할을 써서, S3 게이트웨이 엔드포인트로 같은 버킷에 HEAD를 보낸다.
- 버킷의 `blocks/bench-…/`에 1KiB 객체 1,280개를 올려 HEAD(`ChecksumMode=ENABLED`)하고, 끝나면 지웠다.
- commit의 블록 조회는 앱과 같은 규칙으로 흉내 냈다. commit마다 HEAD를 N개씩 보내고, 모든 commit이 자리 40개(최대 1초 대기)를 나눠 쓴다. 하나라도 자리를 못 받으면 조회 전체가 실패하고, file-service처럼 0.5초·1초 뒤 처음부터 다시(최대 3번) 한다. 각 3번 재서 중간값이다.
- 스크립트는 `.scripts/perf-test/001-upload/s3-head.py`, 결과 원본은 `results/aws-s3-head.json`(2026-10-18).

**HEAD 하나**: p50 14ms, p90 17ms, p99 67ms.

| 동시 | 초당 HEAD | HEAD 하나 p50 / p99 |
|---|---|---|
| 1 | 68 | 14 / 52ms |
| 8 | 491 | 15 / 64ms |
| 16 | 750 | 19 / 102ms |
| 32 | 731 | 34 / 218ms |
| 64 | 687 | 38 / 426ms |

| commit의 블록 조회 | commit당 8개 | commit당 32개 |
|---|---|---|
| 5GiB 파일 1개 (블록 1,280개) | 2.3초 | 1.8초 |
| commit 10개 동시 × 블록 100개 | 1.35초 | 1.38초 |
| commit 10개 동시 × 블록 1,280개 | 17.2초 | 17.0초 |
| commit 30개 동시 × 블록 100개 | 4.1초 | 4.1초 |

- 모든 경우에 자리 거절·재시도·실패는 0번이었다. HEAD가 14ms라 자리가 빨리 돈다.
- 처리량은 동시 16개 근처에서 멈춘다(초당 약 740개). 같은 접두어의 S3 한도(초당 5,500건)보다 훨씬 낮아, 측정 쪽(Python, 2 vCPU)의 한계로 보인다. storage-service(Java, demo 0.5 vCPU)의 한계는 따로 재지 않았다.
- commit이 겹치면 commit당 개수는 상관없다. 인스턴스 전체 처리량을 나눠 쓴다.
- S3가 장애 등으로 HEAD 하나에 100ms 넘게 느려지면, 8개에서도 commit 10개가 겹칠 때 1초 안에 자리를 못 받는 조회가 생길 수 있다.

## 4. 배포·구글 드라이브·네이버 MYBOX 비교

**아직 재지 않았다.** 아래 방법으로 잴 예정이다.

- 1~3만 셋을 비교한다. 구글 드라이브와 MYBOX는 한 사람이 브라우저로 올리는 것만 잴 수 있어, 4~6은 배포한 서비스에서만 이 스크립트로 잰다.
- 같은 PC·같은 네트워크에서 브라우저로, 1~3과 같은 파일(바탕화면 `업로드-테스트`의 `큰 파일`·`작은 파일`·`혼합 폴더`)을 올리고 시작부터 완료 표시까지의 시간을 잰다. 배포한 서비스도 WEB으로 올린다.
- 5GiB는 업로드 대역폭이 거의 전부를 정하므로, 셋을 번갈아 같은 시간대에 잰다.
- MYBOX 웹의 파일 하나 최대 크기가 5GiB보다 작으면 1은 배포·구글 드라이브만 비교한다.

## 5. 측정의 한계

- 로컬 `dev` 프로필은 SQL을 전부 stdout에 찍는다(`show_sql`). 쿼리마다 드는 고정 비용이 운영보다 크게 잡힌다.
- LocalStack은 AWS S3와 지연 특성이 다르다. HEAD 관련 수치는 [3](#3-head-동시-요청-수)의 AWS 측정을 기준으로 본다.
- 같은 이미지라도 측정한 날·서비스를 다시 띄운 직후인지에 따라 절대 시간이 달랐다. 전후 비교는 같은 날 연달아 잰 값끼리만 한다.
