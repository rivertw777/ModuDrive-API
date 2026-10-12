# 드라이브 용량 한도 동시성·성능 테스트

같은 드라이브로 업로드 commit이 동시에 들어올 때 용량 한도가 지켜지는지 확인한 테스트다. 파일 수가 늘 때 commit과 사용량 조회가 얼마나 느려지는지도 같이 쟀다.

## 1. 테스트 시나리오

| 시나리오 | 하는 일 | 확인할 것 |
|---|---|---|
| 동시 commit 20개 | 한도 10MiB 드라이브에 서로 다른 1MiB 블록 20개를 먼저 올린 뒤, commit 20개를 barrier로 동시에 보냄 | 성공한 commit 수, 실제 사용량, 카운터(`used_bytes`) = 실제 합계 |
| 동시 commit 2개 | 한도 1.5MiB 드라이브에 1MiB 파일 2개를 동시에 commit | 성공한 commit 수 |
| 파일 수에 따른 지연 | 드라이브에 파일 0 / 1천 / 1만 / 10만 개(`/bulk` 폴더)를 넣고, 루트에 빈 파일 commit과 사용량 조회를 순차 호출 | commit·사용량 조회 p50/p95 |

- 동시성 시나리오는 라운드마다 새 드라이브를 만들어 10라운드 반복한다.
- 빈 파일 commit에는 블록이 없어서 storage-service를 호출하지 않는다. 그래서 측정되는 건 DB 비용뿐이다.
- 서비스는 compose 네트워크 안에서 `X_USER_ID` 헤더로 file-service(10012)와 storage-service(10013)를 직접 호출한다.

레포 루트에서, 인프라와 서비스가 떠 있는 상태로 실행한다. 비교할 코드로 file-service를 띄우는 방법은 `.scripts/perf-test/scenario.sh deploy <label>`이다.

```bash
.scripts/perf-test/003-quota-concurrency/run.sh 10 20 1048576 10485760     # 동시 commit 20개 × 10라운드 (한도 10MiB)
.scripts/perf-test/003-quota-concurrency/run.sh 10 2 1048576 1572864       # 동시 commit 2개 × 10라운드 (한도 1.5MiB)

# 파일 수별 드라이브 만들기: user_id를 정해 bulk.sh로 만들고 "파일수 user_id"를 한 줄씩 적어 둔다
for n in 0 1000 10000 100000; do u=$(python3 -c 'import uuid;print(uuid.uuid4())'); .scripts/perf-test/003-quota-concurrency/bulk.sh $u $n; echo "$n $u"; done > /tmp/bulk-users.txt
.scripts/perf-test/003-quota-concurrency/perf.sh asis /tmp/bulk-users.txt      # 드라이브마다 100회
.scripts/perf-test/003-quota-concurrency/perf2.sh asis /tmp/bulk-users.txt     # 가장 작은·큰 드라이브만 300회 × 3번
```

스크립트는 `.scripts/perf-test/003-quota-concurrency/`, 결과 원본은 그 아래 `results/`에 있다. 측정 후 `run.sh`가 만든 드라이브는 `scenario.sh cleanup`으로 지운다. `bulk.sh` 드라이브는 실제 한도와 같은 20GiB라서 cleanup에 걸리지 않으므로 user_id로 따로 지운다.

## 2. 테스트 결과

2026-10-11. 개선 전은 `dev`(aa40bde)로, 트랜잭션 밖에서 `SUM(file_size)`를 읽고 판단했다. 개선 후는 `namespace.used_bytes` 카운터를 두고 트랜잭션 마지막에 조건부 UPDATE(`used + Δ ≤ quota`)로 확정한다.

| 시나리오 | 개선 전 | 개선 후 |
|---|---|---|
| 동시 commit 20개, 한도 10MiB | 20개 전부 성공, 사용량 20MiB (한도 200%), 10/10라운드 | 정확히 10개 성공, 나머지는 "저장 공간이 부족합니다", 10/10라운드 |
| 동시 commit 2개, 한도 1.5MiB | 2개 다 성공 (한도 133%), 10/10라운드 | 1개만 성공, 10/10라운드 |
| 카운터 = 실제 합계 | (카운터 없음) | 모든 라운드에서 일치 |

| p50 (300회 × 3번의 평균) | 파일 0개 | 파일 10만 개 |
|---|---|---|
| 사용량 조회 개선 전 | 4.1ms | 14.7ms |
| 사용량 조회 개선 후 | 3.8ms | 3.5ms |
| commit 개선 전 | 14.2ms | 24.0ms |
| commit 개선 후 | 16.4ms | 16.3ms |

- 개선 전에는 파일이 늘수록 commit과 사용량 조회가 같이 느려졌다. 사용량 SQL이 파일 10만 개에서 21ms였다. 개선 후에는 둘 다 평탄하고, 사용량은 카운터 한 행만 읽는다(0.05ms).
- 처음에 파일 10만 개를 루트 한 폴더에 넣었더니 플래너가 이름 조회에 Seq Scan을 골라 commit이 60ms가 나왔다. 그래서 측정용 파일은 `/bulk` 폴더에 넣고, commit은 루트로 보낸다.
