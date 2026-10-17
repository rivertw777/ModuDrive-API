# 파일 업로드 성능 테스트

WEB과 같은 순서로 파일을 올리면서([001 2](../spec/001-file-upload-spec.md#2-파일-업로드)) 단계마다 걸린 시간과 DB·Redis 호출 수를 잰 테스트다. 큰 파일 하나와 작은 파일 여러 개를 각각 올려, 블록 수와 파일 수에 비례해 늘던 호출을 줄인 전후를 비교했다.

## 1. 테스트 시나리오

| 시나리오 | 하는 일 |
|---|---|
| 큰 파일 | 1GiB 파일 1개(4MiB 블록 256개) |
| 작은 파일 | 10KiB 파일 1,000개(파일마다 블록 1개), commit은 100개씩 10번 |

한 번 올릴 때 WEB처럼 네 단계를 차례로 밟고, 단계마다 시간·DB 쿼리 수(pg_stat_statements)·Redis 왕복 수(`INFO commandstats`)를 잰다.

| 단계 | 하는 일 |
|---|---|
| commit1 | 처음 commit — 모든 블록이 `needBlocks`로 돌아온다 |
| presign | 업로드 URL 요청 (64개씩) |
| put | S3(LocalStack)에 블록 PUT (동시 6개) |
| commit2 | 2차 commit — S3에서 블록을 확인하고 버전을 만든다 |

- 라운드마다 새 드라이브에 올리고, 끝나면 그 드라이브의 S3 블록을 지운다. 시나리오마다 워밍업 1번 뒤 5라운드, 표는 중앙값이다.
- 서비스는 compose 네트워크 안에서 `X_USER_ID` 헤더로 file-service(10012)와 storage-service(10013)를 직접 호출한다.
- Redis 왕복은 `EVALSHA`·`ZADD`·`ZMSCORE` 같은 명령 수다. `upload-count.lua`가 안에서 실행하는 `INCR`·`PEXPIRE`는 왕복이 아니라 빼고 센다.

레포 루트에서, 인프라와 서비스가 떠 있는 상태로 실행한다.

```bash
.scripts/perf-test/scenario.sh up                        # pg_stat_statements + bench 컨테이너
.scripts/perf-test/004-upload/run.sh image tobe          # 작업 트리로 file·storage-service 이미지를 만들어 tobe로 태그
.scripts/perf-test/004-upload/run.sh deploy tobe         # 그 이미지로 두 서비스를 다시 띄움
.scripts/perf-test/004-upload/run.sh tobe-big 5 big 256
.scripts/perf-test/004-upload/run.sh tobe-small 5 small 1000 10240
python3 .scripts/perf-test/004-upload/summary.py tobe-big tobe-small   # 레포 루트가 아니라 004-upload/에서
.scripts/perf-test/scenario.sh cleanup                   # 측정용 드라이브 삭제
```

스크립트는 `.scripts/perf-test/004-upload/`, 결과 원본(라운드마다 JSON 한 줄)은 그 아래 `results/`에 있다.

## 2. 테스트 결과

2026-10-17, 로컬(WSL2, LocalStack). 개선 전은 `dev`(35ebbd69), 개선 후는 아래 네 가지를 바꾼 것이다(#681).

| 바꾼 것 | 개선 전 | 개선 후 |
|---|---|---|
| URL 발급의 Redis 호출 | 블록마다 한도 세기 + 발급 시각 기록 (64개 요청에 128번) | 요청마다 한도 세기 1번 + 인덱스 키마다 `ZADD` 1번 (최대 17번) |
| URL 발급의 하루 한도 | 블록마다 세다가 넘으면 실패 — 앞 블록들은 URL 없이 한도만 깎임 | 요청 전체를 한 번에 세고, 안 들어가면 하나도 안 셈 |
| commit 사전 검사 | 파일마다 `uploadId` 조회 + 자리 조회 (100개에 200번) | 묶음마다 `uploadId` 1번 + 올라갈 폴더마다 1번 |
| commit의 S3 HEAD | 동시 8개 | 동시 32개, S3 커넥션 풀 50 → 85(벌크헤드 자리 합) |

**큰 파일 (1GiB, 블록 256개)**

| 단계 | 개선 전 | 개선 후 |
|---|---|---|
| presign | 818ms, Redis 512번 | **243ms (-70%)**, Redis 23번 |
| commit2 | 1,367ms, DB 271번 | 1,311ms (-4%), DB 271번 |
| commit1 | 99ms, DB 4번 | 133ms, DB 4번 |
| put | 9.8초 | 8.5초 |

**작은 파일 (10KiB × 1,000개)**

| 단계 | 개선 전 | 개선 후 |
|---|---|---|
| commit1 | 3,232ms, DB 2,020번 | **485ms (-85%), DB 40번** |
| presign | 1,688ms, Redis 2,000번 | **377ms (-78%)**, Redis 47번 |
| commit2 | 26.8초, DB 13,021번 | 23.3초 (-13%), DB 11,041번 |
| put | 5.6초 | 5.6초 |

- **URL 발급과 1차 commit**은 호출 수가 줄어든 만큼 빨라졌다. 블록 수·파일 수에 비례하던 호출이 요청·묶음마다 몇 번으로 바뀌었다.
- **큰 파일의 2차 commit**은 시간이 거의 그대로다. 시간 대부분이 블록 256개의 HEAD인데, 로컬 LocalStack은 동시 요청을 늘려도 HEAD가 빨라지지 않았다(8개 → 32개에서 차이 없음). HEAD 동시 요청 수는 HEAD 하나가 네트워크를 오가는 시간(AWS S3에서 수십 ms)을 겹치려는 것이라, 로컬에서는 효과가 드러나지 않는다. AWS에서 다시 재야 한다.
- 큰 파일의 commit1(99ms → 133ms)은 코드 차이가 아니다. 블록 64개로 다시 재 보니 같은 개선 후 이미지가 띄워 둔 채로는 61~72ms, 다시 띄운 직후에는 105~152ms였다(개선 전은 다시 띄운 직후 105~132ms). 워밍업 1라운드로는 JVM이 덜 데워져 생기는 편차다.
- put은 S3로 바로 가는 단계라 바뀐 것이 없다(9.8초 → 8.5초는 LocalStack 편차).
- `reWriteBatchedInserts`(blocklist 배치를 여러 행 INSERT 하나로)도 같이 재 봤다. 큰 파일 2차 commit의 DB 쿼리 집계는 271번 → 32번으로 줄지만 시간은 차이가 없어(1,289ms), #667의 측정(배치 50과 잡음 수준 차이)대로 켜지 않았다. 표의 개선 후는 이 옵션 없이 다시 잰 값이다.

## 3. 남은 병목: 작은 파일의 2차 commit

1,000개 기준 23초로 여전히 업로드 전체에서 가장 길다. 파일 100개짜리 commit 하나를 pg_stat_statements로 보면 DB 실행 시간은 합쳐서 약 130ms인데, 요청은 2.3초가 걸린다.

| 파일마다 (commit2) | 쿼리 |
|---|---|
| 폴더 행 잠금, 파일 행 잠금 | 2 |
| 블록 행 잠금, 블록 참조 upsert | 2 |
| 파일 insert(새 파일) 후 버전 연결 update | 2 |
| 버전 insert, blocklist insert | 2 |
| 최근 문서함(file_access) 조회 + insert | 2 |
| 사용량 확정(namespace update) | 1 |
| 트랜잭션 commit | 1 |

- 파일마다 **트랜잭션을 따로** 연다([001 2-3](../spec/001-file-upload-spec.md#2-3-commit) — 한 파일이 실패해도 나머지는 저장되게). 그래서 쿼리 11번과 commit이 파일 수만큼 그대로 반복되고, 시간은 쿼리 실행이 아니라 왕복과 트랜잭션 commit에서 나간다.
- 같은 폴더에 올리는 파일 100개가 상위 폴더 행을 각자 다시 잠그고, 사용량 행(namespace)도 파일마다 갱신한다.
- 줄이려면 묶음 하나를 트랜잭션 하나로 묶고 파일마다 savepoint를 두는 식으로 바꿔야 한다. 실패 범위·잠금 순서([001 4](../spec/001-file-upload-spec.md#4-동시-요청과-잠금))가 바뀌는 설계 변경이라 이번에는 하지 않았다.

**로컬 측정의 한계**

- 로컬 `dev` 프로필은 SQL을 전부 stdout에 찍는다(`show_sql`). 쿼리마다 드는 고정 비용이 운영보다 크게 잡힌다.
- LocalStack HEAD는 AWS S3와 지연 특성이 달라, HEAD 동시 요청 수의 효과는 AWS에서만 볼 수 있다.
