# S3 장애 테스트

S3가 장애를 일으킬 때 storage-service가 버티는지, 파일을 잃거나 망가뜨리지 않는지 확인한 테스트다. S3 호출에 [006 2-4](../spec/006-resilience-spec.md#2-4-s3-호출-storage-service)의 방어(시간 제한, 재시도 제한, 경로별 벌크헤드·서킷 브레이커, 최근 10초 기준 서킷과 느린 호출 기준)를 걸고, S3 때문인 503을 부르는 쪽 서킷이 세지 않게 한 상태에서 실행했다.

## 1. 테스트 시나리오

LocalStack 무료 플랜에는 장애 주입 기능이 없어서, storage-service·브라우저와 LocalStack S3 사이에 S3 장애를 흉내 내는 프록시(`.scripts/fault-test/002-s3-failure/`)를 끼웠다. storage-service는 `STORAGE_S3_ENDPOINT`로, 브라우저의 블록 PUT은 `STORAGE_S3_PUBLIC_ENDPOINT`로 프록시를 거친다. 업로드 시나리오(`put`)는 실제 Chromium에서 WEB의 `upload-file.ts`를 번들해 `uploadGroup`을 그대로 실행한다(`put.mjs`, Playwright·rolldown은 옆의 ModuDrive-WEB 체크아웃에서 쓴다). commit·URL 요청만 `client.py`가 서비스에 직접 보낸다.

| 경로 | S3 호출 |
|---|---|
| 블록 업로드 | 브라우저가 presigned URL로 `PutObject` |
| commit의 블록 확인 | `HeadObject` · `ListObjectsV2` |
| 다운로드·zip·미리보기 | 블록마다 `GetObject` |
| 블록 삭제 (SQS 소비) | `HeadObject` + 조건부 `DeleteObject` |

| 장애 시나리오 | 프록시의 응답 | 대상 | 확인할 것 |
|---|---|---|---|
| S3 일시 장애 | `503 SlowDown` | 블록 확인·다운로드 (5번 연속) | 얼마 만에 어떤 응답으로 실패하는지, 서킷이 열리는지 |
| 응답 없음 | 연결만 받고 응답하지 않음 | 블록 확인·다운로드 | 언제 어떤 응답으로 실패하는지, S3에 동시에 매달린 호출 수 |
| 경로 간 간섭 | 미리보기 `GetObject`만 응답하지 않음 | 미리보기 → 블록 확인 · 블록 URL 발급 | 미리보기가 몰려 S3 커넥션을 다 쓰면 정상인 블록 확인도 막히는지, S3를 쓰지 않는 URL 발급은 그대로인지 |
| 경로 간 간섭 (gateway 경유) | 미리보기 `GetObject`만 응답하지 않음 | gateway를 거친 미리보기 → 업로드 URL 발급 | gateway의 storage-service 라우트 서킷·벌크헤드가 미리보기 때문에 URL 발급까지 막는지 |
| 짧은 장애 후 복구 | `HeadObject`·`ListObjectsV2`에 8초 동안 503, 이후 정상 | 블록 확인 (1초 간격 20번) | 복구 뒤의 commit이 바로 성공하는지 |
| commit 중 S3 일시 장애 | `HeadObject`·`ListObjectsV2`에 30초 동안 503, 이후 정상 | 브라우저 업로드 (파일 10개 한 묶음) | 파일별 503을 받은 파일을 다시 commit해 성공하는지 |
| 다운로드 도중 끊김 | N번째 블록 `GetObject`에서 연결 끊음 | 다운로드 | 잘린 파일이 성공처럼 받아지는지 |
| 깨진 데이터 | `GetObject` 본문 1바이트 변조 | 다운로드 | 깨진 파일을 그대로 내려주는지 |
| 권한 오류 | `403 AccessDenied` | 블록 삭제 | 메시지를 몇 번 받은 뒤 DLQ로 가는지, DLQ 사유 |
| 삭제 후 응답 유실 | `DeleteObject`를 처리한 뒤 응답하지 않음 | 블록 삭제 | 재배달 때 정상 종료하는지 |
| 업로드 후 응답 없음 | `PutObject`를 저장한 뒤 응답하지 않음 (20초 뒤 정상) | 블록 업로드 | 응답 없는 PUT을 끊고 다시 보내는지, 다시 보낸 PUT이 `412`를 받는지 |
| 업로드 PUT 일시 장애 | `PutObject`에 `503` (20초 뒤 정상) | 블록 업로드 | 언제 다시 보내고, 복구 뒤 성공하는지 |
| 업로드 후 연결 끊김 | `PutObject`를 저장한 뒤 응답 없이 연결을 끊음 (5초 동안) | 블록 업로드 | 다시 보낸 PUT이 `412`를 받는지, 그 뒤 commit이 성공하는지 |

테스트 순서:

1. 기준점 기록: S3 객체 수, DLQ 메시지 수, 서킷 상태 (storage-service의 `s3Lookup`·`s3Read`·`s3Delete`, file-service의 `storageServiceCircuitBreaker`가 앞 시나리오에서 열려 있으면 정상 업로드·다운로드로 닫은 뒤 다시 실행. 열린 서킷은 호출이 와야 넘어가므로 기다리기만 해서는 닫히지 않는다)
2. 프록시를 해당 시나리오로 변경
3. 해당 경로 요청: 업로드(URL 발급 → PUT → commit), 다운로드, 파일 영구 삭제
4. 요청이 끝나거나 메시지가 처리될 때까지 대기
5. 결과 확인: S3 호출 수, S3에 동시에 매달린 호출 수(프록시 기준), 응답 코드, 걸린 시간, 받은 파일의 해시, 서킷 상태, DLQ 메시지와 사유
6. DLQ를 비우고 프록시를 정상으로 되돌림

걸린 시간을 보는 시나리오는 3번씩 실행해 범위로 적는다.

레포 루트에서, 인프라와 서비스가 떠 있는 상태로 실행한다.

```bash
.scripts/fault-test/002-s3-failure/scenario.sh up                         # 프록시를 띄우고 storage-service를 프록시 경유로 재시작
REPEAT=5 .scripts/fault-test/002-s3-failure/scenario.sh upload unavailable    # S3 일시 장애 (블록 확인)
REPEAT=5 .scripts/fault-test/002-s3-failure/scenario.sh download unavailable  # S3 일시 장애 (다운로드)
CLIENT_TIMEOUT=300 .scripts/fault-test/002-s3-failure/scenario.sh upload hang    # 응답 없음 (블록 확인)
CLIENT_TIMEOUT=300 .scripts/fault-test/002-s3-failure/scenario.sh download hang  # 응답 없음 (다운로드)
.scripts/fault-test/002-s3-failure/scenario.sh flood 250 60               # 경로 간 간섭 (미리보기 250개, 60초)
VIA_GATEWAY=1 .scripts/fault-test/002-s3-failure/scenario.sh flood 250 60  # 경로 간 간섭 (gateway 경유)
REPEAT=20 GAP=1 .scripts/fault-test/002-s3-failure/scenario.sh upload unavailable 0 head,list 0 8  # 짧은 장애 후 복구 (8초 뒤 정상, 1초 간격 commit 20번)
VIA_GATEWAY=1 REPEAT=190 GAP=1 CLIENT_TIMEOUT=30 .scripts/fault-test/002-s3-failure/scenario.sh download unavailable 0 get 0 240  # 알림 (240초 장애, gateway 경유 다운로드 약 1초마다)
.scripts/fault-test/002-s3-failure/scenario.sh download cut 0 get 1       # 다운로드 도중 끊김 (2번째 블록)
.scripts/fault-test/002-s3-failure/scenario.sh download corrupt           # 깨진 데이터
WAIT=120 .scripts/fault-test/002-s3-failure/scenario.sh purge denied      # 권한 오류
WAIT=300 .scripts/fault-test/002-s3-failure/scenario.sh purge lost 0 delete  # 삭제 후 응답 유실
FILES=10 .scripts/fault-test/002-s3-failure/scenario.sh put unavailable 0 head,list 0 30  # commit 중 S3 일시 장애 (30초 뒤 정상)
PUT_CAP=300 .scripts/fault-test/002-s3-failure/scenario.sh put lost 0 put 0 20  # 업로드 후 응답 없음 (20초 뒤 정상)
.scripts/fault-test/002-s3-failure/scenario.sh put unavailable 0 put 0 20  # 업로드 PUT 일시 장애 (20초 뒤 정상)
.scripts/fault-test/002-s3-failure/scenario.sh put drop 0 put 0 5         # 업로드 후 연결 끊김 (5초 뒤 정상)
.scripts/fault-test/002-s3-failure/scenario.sh down                       # 프록시를 지우고 storage-service를 LocalStack 직결로 되돌림
```

## 2. 테스트 결과

| 장애 시나리오 | 결과 | 걸린 시간 |
|---|---|---|
| 정상 | 업로드·다운로드 성공 | 즉시 |
| S3 일시 장애 | 블록 확인은 503, 다운로드는 503(`STORAGE_UNAVAILABLE`). 호출마다 SDK가 2번 시도. 블록 확인은 `s3Lookup` 서킷이 열림. 다운로드는 10초 안에 호출 10번이 차지 않아 서킷이 열리지 않음. file-service 서킷은 닫힘 | 블록 확인 0.03~0.13초, 다운로드 0.09~0.97초 |
| 응답 없음 | 블록 확인은 503, 다운로드는 503(`STORAGE_UNAVAILABLE`). 시도마다 5초에서 끊고 1번 더 시도. S3에 동시에 매달린 호출은 2를 넘지 않음 | 10.0~10.2초 |
| 경로 간 간섭 | 미리보기 250개 모두 503(`STORAGE_UNAVAILABLE`) — 210개는 `s3Read` 자리가 없어 바로, 40개는 시간 초과로. 끝나지 않은 것 없음. S3 동시 호출 43. `s3Read` 서킷만 열림. 블록 확인 60번, URL 발급 60번 모두 성공 | 블록 확인 최대 48~71ms, URL 발급 최대 29~35ms |
| 경로 간 간섭 (gateway 경유) | 미리보기 250개 모두 storage-service까지 가서 503 — 210개는 `s3Read` 자리가 없어 바로, 40개는 시간 초과로. `X-Dependency-Unavailable`이 붙은 503이라 gateway 서킷은 닫힌 채 그대로 전달. 업로드 URL 발급·블록 확인 60번씩 모두 성공, gateway 벌크헤드 거절 없음 | URL 발급 최대 50~151ms |
| 짧은 장애 후 복구 | 장애 중 commit 5번이 503 → `s3Lookup` 서킷이 열림. 복구 뒤에도 서킷이 열린 약 9초 동안 commit이 S3를 부르지 않고 바로 503(대기 없음), 반열림 시험 호출 뒤 닫혀 성공. file-service 서킷은 닫힘 | 503 0.02~0.14초, 성공 0.04~0.19초 |
| 다운로드 도중 끊김 | 200으로 시작해 1번째 블록 뒤에 끊김 (클라이언트가 잘린 것을 알아챔) | 즉시 |
| 깨진 데이터 | 블록이 해시와 맞지 않아 500(`STORAGE_ERROR`), 내려간 바이트 없음 | 즉시 |
| 권한 오류 | 4번 받은 뒤 DLQ (`Retries exhausted after 4 attempts`). 403은 서킷에 실패로 세지 않음 | 약 10초 |
| 삭제 후 응답 유실 | 블록 삭제됨. 재배달 뒤 정상 종료, DLQ 없음 | 약 26~39초 |
| commit 중 S3 일시 장애 | 파일 10개 모두 commit이 503(`retryAfter: 10`) → 10.2~14.7초 간격(10초 + 0~50% 지터)으로 다시 commit해 모두 성공. 일시 중지 없음 | 29.2~40.4초 |
| 업로드 후 응답 없음 | 첫 PUT을 30초 유휴 제한에서 끊고, 다시 보낸 PUT이 `412`, commit 성공 | 32.5~32.8초 |
| 업로드 PUT 일시 장애 (20초 뒤 정상) | 빠른 재시도 3번 뒤 일시 중지, 30초 간격으로 다시 보내 복구 뒤 성공, commit 성공 | 44.9~55.4초 |
| 업로드 후 연결 끊김 | 다시 보낸 PUT이 `412`, commit 성공 | 5.3~5.6초 |

### 알림

S3 `GetObject`가 240초 동안 `503`을 돌려주는 동안 gateway를 거쳐 약 1초마다 다운로드하고, Prometheus·Alertmanager의 알림 상태를 15초마다 확인했다. 알림은 실제 Discord 채널로 갔다(전송 실패 0).

S3 호출 실패 알림을 넣기 전:

| 알림 | Discord 전송 (장애 시작 기준) | 해제 전송 (S3 복구 기준) |
|---|---|---|
| 요청 실패율 높음 (`upstream=storage-service`) | 5분 29초~5분 44초 | 6분 28초~6분 43초 |

- gateway 서킷은 열리지 않았다(storage-service의 503에 `X-Dependency-Unavailable`이 붙어 있어 세지 않는다).
- storage-service의 `s3Read` 서킷은 다운로드가 약 1초마다라 최근 10초의 호출 10번을 겨우 채워, 240초 동안 막은 호출이 7건이었다(S3 호출이 실패한 다운로드 145건). 서킷 열림 알림은 `pending`에서 끝났고, 알림만으로는 S3가 원인인 줄 알 수 없었다.

S3 호출 실패 알림([009 4-11](../spec/009-discord-alert-spec.md#4-11-s3-호출-실패))을 넣은 뒤:

| 알림 | Discord 전송 (장애 시작 기준) | 해제 전송 (S3 복구 기준) |
|---|---|---|
| S3 호출 실패 (`storage-service` · `s3Read`) | 2분 30초~2분 45초 | 2분 29초~2분 44초 |
| 요청 실패율 높음 (`upstream=storage-service`) | 3분 30초~3분 45초 | 5분 29초~5분 44초 |

- 요청 실패율 알림은 규칙이 같은데도 두 측정에서 전송 시각이 2분 차이 났다(각 1번 측정).
