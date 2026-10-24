# 폴더 연쇄 작업 성능 테스트

폴더를 이동·휴지통·복원·영구 삭제할 때, 하위 파일 수에 따라 SQL 수와 응답 시간이 어떻게 늘어나는지 확인한 테스트다. 작업 중 같은 드라이브의 업로드가 얼마나 기다리는지도 같이 쟀다.

## 1. 테스트 시나리오

하위 파일 N개 폴더(하위 폴더 10개에 나눠 담음, 파일당 버전 1개·블록 3개)를 SQL로 직접 만들고, file-service를 compose 네트워크 안에서 `X_USER_ID` 헤더로 직접 호출했다.

| 시나리오 | 하는 일 | 확인할 것 |
|---|---|---|
| 이동 | `PATCH /api/v1/files/{id}/path` | SQL 수, 응답 시간 |
| 휴지통 / 복원 | `DELETE /api/v1/files/{id}`, `PATCH …/restore` | SQL 수, 응답 시간 |
| 영구 삭제 | 휴지통에 넣은 뒤 `DELETE …/purge` | SQL 수, 응답 시간, 끝난 뒤 드라이브 사용량 = 실제 파일 크기 합 |
| 영구 삭제 중 업로드 | 영구 삭제가 도는 동안 다른 클라이언트가 드라이브 루트에 빈 파일 commit을 연달아 보냄 | 가장 오래 기다린 commit |
| 이동 중 업로드 | 이동이 도는 동안 그 폴더 안으로 commit을 연달아 보냄 | 가장 오래 기다린 commit |

- SQL 수는 `pg_stat_statements`로 요청 하나의 `file_service` 문장 수를 셌다. 백그라운드 outbox 문장은 빼고, BEGIN/COMMIT도 세지 않았다.
- 비교는 같은 Postgres에서 file-service 이미지만 바꿔 했다.

레포 루트에서, 인프라와 서비스가 떠 있는 상태로 실행한다.

```bash
.scripts/perf-test/scenario.sh up                         # pg_stat_statements 켜기 + 측정용 컨테이너
.scripts/perf-test/scenario.sh deploy asis                # 비교 기준 코드로 빌드·재기동 (브랜치를 바꿔 가며)
.scripts/perf-test/scenario.sh cascade asis 100 1000      # N=100, 1,000 각 3회
ROUNDS=1 SKIP_WAIT=1 .scripts/perf-test/scenario.sh cascade asis10k 10000
.scripts/perf-test/scenario.sh contend asis 1000          # 작업 중 업로드 대기
.scripts/perf-test/scenario.sh wait                       # 서버에 남은 트랜잭션이 끝날 때까지
.scripts/perf-test/scenario.sh deploy tobe                # 개선 코드로 바꿔 같은 명령 반복
.scripts/perf-test/scenario.sh cleanup                    # 측정용 데이터 삭제
.scripts/perf-test/scenario.sh down                       # pg_stat_statements 끄기
```

스크립트는 `.scripts/perf-test/002-folder-cascade/`에 있고(공용 헬퍼는 `.scripts/perf-test/bench.py`), 결과 원본은 그 아래 `results/`에 json으로 남는다.

## 2. 테스트 결과

2026-10-11. 개선 전은 `dev`(65bfe24), 개선 후는 하위 행을 접두사 벌크 SQL로 처리하고 블록 참조를 SQL 안에서 집계해 한 번에 해제하도록 바꾼 코드다. 칸 값은 `SQL 수 / 응답 시간 중앙값`이다. 개선 전 1만 개는 영구 삭제 한 번에 8분이 걸려 1회만 쟀다.

| 작업 | N=100 개선 전 → 후 | N=1,000 개선 전 → 후 | N=10,000 개선 전 → 후 |
|---|---|---|---|
| 이동 | 114 / 89ms → 5 / 32ms | 1,014 / 1.19s → 5 / 40ms | 10,014 / 56.7s → 5 / 184ms |
| 휴지통 | 113 / 97ms → 4 / 29ms | 1,013 / 1.19s → 4 / 34ms | 10,013 / 54.3s → 4 / 172ms |
| 복원 | 114 / 110ms → 5 / 39ms | 1,014 / 1.21s → 5 / 39ms | 10,014 / 57.5s → 5 / 169ms |
| 영구 삭제 | 1,358 / 967ms → 18 / 112ms | 13,058 / 11.3s → 18 / 177ms | 130,058 / 495s → 18 / 1.36s |

| 작업 중 업로드 (각 3회) | 개선 전 | 개선 후 |
|---|---|---|
| 1천 개 영구 삭제 중, 드라이브 루트에 | 최대 4.0~4.8초 대기 | 최대 39~64ms |
| 1천 개 이동 중, 그 폴더 안에 | 최대 1.27~1.38초 | 최대 53~79ms |
| 1만 개 영구 삭제 중, 드라이브 루트에 | 영구 삭제가 10분 HTTP 타임아웃 안에 안 끝남 | 최대 38~47ms (영구 삭제 1.6~2.6초) |

- 개선 전에는 파일이 10배(1천 → 1만) 늘면 시간이 44~47배 늘었다. 문장마다 flush가 영속성 컨텍스트에 올라온 하위 엔티티 전부를 dirty check하기 때문이다.
- 개선 전 영구 삭제는 파일마다 드라이브 용량 행을 UPDATE했다. 그래서 첫 파일 이후로는 같은 드라이브의 업로드가 전부 기다렸다. 개선 후에는 그 UPDATE가 트랜잭션 마지막 한 번이다.
- 모든 라운드에서 끝난 뒤 `namespace.used_bytes`가 실제 파일 크기 합과 일치했다.

## 3. 측정 중 발견한 것

- 개선 후 첫 측정에서 1만 개 영구 삭제가 3회 중 2회는 130초, 1회는 1.5초로 들쭉날쭉했다.
- `slowest_statements.py`로 보니 블록 참조 해제 문장 하나가 원인이었다. `explain.py`로 실행 계획을 보니, UPDATE가 CTE 두 개를 서로 조인하는데 플래너가 CTE 크기를 65행으로 추정했다(실제 30,000행). 그래서 중첩 루프(30,000 × 30,000)를 고를 때가 있었다.
- UPDATE가 잠금 CTE와 `block` 기본키만 조인하도록 바꾸자 3회 모두 1.34~1.50초로 안정됐다.
- 한계: 10만 개 이상 폴더는 재지 않았다. 한 트랜잭션이라 잠금 시간은 파일 수에 대략 비례한다.

## 4. 다시 잴 때 주의할 것

- 클라이언트가 타임아웃되거나 중단돼도 서버 트랜잭션은 계속 돈다. 다음 측정 전에 `scenario.sh wait`로 기다려야 한다.
- 측정 중에 gradle 테스트처럼 무거운 작업을 같이 돌리면 응답 시간이 흔들린다.
- 시드 데이터를 한 폴더에 몰면 플래너 통계가 왜곡된다. `seed()`는 하위 폴더 10개에 나눠 담는다.
- 측정용 드라이브는 `quota_bytes`(1e15, 1e12)로 구분해서 지운다. 스크립트를 새로 추가할 때도 이 규칙을 따른다.

## 5. 큰 드라이브에서 경로 접두사 인덱스

2026-10-12. 위 측정은 측정용 폴더만 든 작은 드라이브에서 했다. 다른 파일이 100만 개 있는 드라이브에 같은 폴더를 두고 다시 재 보니, 벌크 SQL로 바꾼 뒤에도 하위 파일 100개짜리 폴더의 휴지통 이동에 2.2초가 걸렸다.

- 원인: 하위를 고르는 `path LIKE '<prefix>/%'`가 인덱스를 쓰지 못해 드라이브 전체를 읽었다. DB 정렬 규칙(`en_US`)에서는 기존 `(namespace_id, path, active_slot_name)` 인덱스로 접두사 범위 검색을 할 수 없고, 패턴을 `concat()`(STABLE)으로 만들어 실행 계획 시점에 상수도 아니었다.
- 고친 것: `V11__file_path_prefix_idx.sql`로 `(namespace_id, path varchar_pattern_ops)` 인덱스를 `create index concurrently`로 추가하고(트랜잭션 밖에서 실행), 하위를 고르는 쿼리 여섯 개의 패턴을 `||`로 바꿨다.

```bash
.scripts/perf-test/scenario.sh index tobe 100 1000 10000           # BACKGROUND=1000000 (기본), 각 3회
OPS=trash ROUNDS=1 .scripts/perf-test/scenario.sh index original 100 1000 10000   # 행 단위 코드는 휴지통만
```

드라이브 파일 100만 개, 응답 시간 중앙값. 칸 값은 `SQL 수 / 시간`이다. 원본 json은 `.scripts/perf-test/002-folder-cascade/results/2026-10-12/`에 있다.

| 하위 파일 수 | 행 단위 (벌크 SQL 전) | 벌크 SQL, 인덱스 전 | 벌크 SQL, 인덱스 후 |
|---|---|---|---|
| 휴지통 100 | 113 / 945ms | 4 / 2,190ms | 4 / 67ms |
| 휴지통 1,000 | 1,013 / 3.69s | 4 / 2,222ms | 4 / 90ms |
| 휴지통 10,000 | 10,013 / 89.2s | 4 / 2,539ms | 4 / 567ms |
| 영구 삭제 100 | | 18 / 114ms | 18 / 149ms |
| 영구 삭제 1,000 | | 18 / 295ms | 18 / 331ms |
| 영구 삭제 10,000 | | 18 / 3.41s | 18 / 3.35s |

- 휴지통 이동은 이제 폴더 크기만큼만 걸린다(드라이브 크기와 무관).
- 영구 삭제는 인덱스 전후 차이가 없었다(측정 오차 범위). 원인은 따로 확인하지 않았다.
