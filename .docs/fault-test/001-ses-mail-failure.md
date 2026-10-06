# 메일 발송 장애 테스트

SES가 장애를 일으킬 때 mail-service가 메일을 유실하거나 두 번 보내지 않는지 확인한 테스트다.

## 1. 테스트 시나리오

LocalStack 무료 플랜에는 장애 주입 기능이 없어서, mail-service와 LocalStack 사이에 SES 장애를 흉내 내는 프록시(`.scripts/ses-fault/`)를 끼웠다.

| 장애 시나리오 | 프록시의 응답 | 확인할 것 |
|---|---|---|
| SES 서버 장애 | `503 ServiceUnavailable` | 재시도 후 발송 여부 |
| 초당 발송 한도 초과 | `400 Throttling` | 재시도 후 발송 여부 |
| 발신 주소 미인증 | `400 MessageRejected` | 재시도 없이 DLQ 이동 여부 |
| 느리지만 결국 성공 | 15초 대기 후 정상 응답 | 중복 발송 여부 |
| 응답 없음 | 연결만 받고 응답하지 않음 | 시간 제한 처리 여부 |
| 짧은 장애 후 복구 | 4초 동안 503, 이후 정상 | 복구 후 정상 발송 여부 |

테스트 순서:

1. 기준점 기록: 지금까지 보관된 메일 수와 현재 시각
2. 프록시를 해당 시나리오로 변경
3. 새 이메일 주소로 인증 메일 API 요청
4. 메시지가 처리될 때까지 대기
5. 결과 확인: SES 호출 수, 보내진 메일 수, DLQ 메시지와 사유
6. DLQ를 비우고 프록시를 정상으로 되돌림

레포 루트에서, 인프라와 서비스가 떠 있는 상태로 실행한다.

```bash
.scripts/ses-fault/scenario.sh up                  # 프록시를 띄우고 mail-service를 프록시 경유로 재시작
.scripts/ses-fault/scenario.sh unavailable 45      # SES 서버 장애
.scripts/ses-fault/scenario.sh throttle 45         # 초당 발송 한도 초과
.scripts/ses-fault/scenario.sh rejected 30         # 발신 주소 미인증
.scripts/ses-fault/scenario.sh slow 50 15          # 느리지만 결국 성공 (15초)
.scripts/ses-fault/scenario.sh hang 150            # 응답 없음
.scripts/ses-fault/scenario.sh unavailable 30 0 4  # 짧은 장애 후 복구 (4초 뒤 정상)
.scripts/ses-fault/scenario.sh down                # 프록시를 지우고 mail-service를 LocalStack 직결로 되돌림
```

## 2. 테스트 결과

| 장애 시나리오 | 발송된 메일 | 결과 | 걸린 시간 |
|---|---|---|---|
| 정상 | 1통 | 성공 | 즉시 |
| SES 서버 장애 | 0통 | DLQ | 약 11초 |
| 초당 발송 한도 초과 | 0통 | DLQ | 약 21초 |
| 발신 주소 미인증 | 0통 | DLQ | 즉시 |
| 느리지만 결국 성공 | 1통 | 성공 | 약 15초 |
| 응답 없음 | 0통 | DLQ | 약 3분 31초 |
| 짧은 장애 후 복구 | 1통 | 성공 | 약 5초 |

- 한계: 15초 지연을 70초로 늘리면(60초 대기보다 늦음) 2통이 간다.
