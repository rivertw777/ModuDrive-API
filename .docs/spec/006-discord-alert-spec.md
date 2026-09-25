# 디스코드 알림 스펙

이 문서는 **서비스에 문제가 생겼을 때 사람에게 알리는 기능**이 어떤 규칙으로 동작하는지 정의한 문서입니다.
현재 받는 곳은 디스코드 채널입니다.

⚠️ 이 문서가 기준입니다. 코드가 이 문서와 다르면 코드를 고치고, 동작을 바꾸려면 이 문서를 먼저 고칩니다.

---

## 목차

- [1. 알림 발송 과정](#1-알림-발송-과정)
- [2. 지표](#2-지표)
- [3. 규칙](#3-규칙)
- [4. 알림 메시지](#4-알림-메시지)
- [5. 그룹핑과 재발송](#5-그룹핑과-재발송)
- [6. 사용 디스코드 채널](#6-사용-디스코드-채널)
- [7. 사용 알림](#7-사용-알림)
- [8. TODO](#8-todo)

---

## 1. 알림 발송 과정

```mermaid
flowchart LR
    M["서비스<br/>지표(게이지) 노출"]
    P[("Prometheus<br/>TSDB")]
    R["Grafana 알림 규칙<br/>1분마다 평가"]
    A["Grafana Alertmanager<br/>그룹핑 · 대기 · 재발송"]
    D(["Discord<br/>#messaging · #service"])

    M -- "스크레이프 15초" --> P
    P -- "쿼리" --> R
    R -- "조건이 for 동안 계속 참" --> A
    A -- "POST" --> D
```

| 단계 | 하는 일 |
|---|---|
| 서비스 | 알릴 값을 게이지로 내놓는다 |
| Prometheus | 15초마다 긁어 저장 |
| 알림 규칙 | 1분마다 조건 확인 — `for` 동안 계속 맞으면 알림 (중간에 안 맞으면 없던 일) |
| Alertmanager | 같은 알림끼리 한 그룹으로 발송, 안 고치면 4시간마다 다시 |
| Discord | `channel` 라벨이 가리키는 채널의 웹후크로 도착 |

**울리기까지** = `for` + 최대 1분 + 30초.

Alertmanager 컨테이너는 따로 안 띄운다 — Grafana에 내장된 것을 쓴다.

---

## 2. 지표

앱이 `:9464/actuator/prometheus`에 이런 줄을 내놓는 것이 전부다 — **이름 + 라벨 + 숫자**.

```
modudrive_outbox_failed{instance="member-service",queue="mail-verification-requested",reason="SqsException",detail="The specified queue does not exist."} 1
```

Prometheus가 15초마다 긁어 시계열로 쌓고, 규칙은 그걸 PromQL로 본다.

```
max by (instance, queue, reason, detail) (modudrive_outbox_failed) > 0
```

- **`by (...)`에 넣은 라벨만 알림 문구에 쓸 수 있다.** 나머지는 집계에 묻혀 사라진다.
- 라벨 값은 **짧고 종류가 적게**. 값이 다르면 시계열이 하나씩 새로 생기므로 id나 바이트 수 같은 건 넣지 않는다.
- 라벨 조합이 바뀌는 지표는 값만 갱신할 수 없어 등록을 다시 한다(Micrometer `MultiGauge`).
  사라진 조합 = 시계열 소멸 = **알림 해제**.

---

## 3. 규칙

알림 하나 = 프로비저닝 파일의 `rules:` 항목 하나. 정할 건 **조건**과 **`for`** 둘뿐이다.

```yaml
- title: 이벤트 전송 실패                      # 알림 제목
  for: 0m                                    # 얼마나 버텨야 알릴지
  labels: {channel: messaging}               # 어느 채널로 → 6장
  annotations: {summary: ..., action: ..., query: ...}   # 메시지 문구 → 4장
  data:
    - expr: max by (instance, queue, reason, detail) (modudrive_outbox_failed)   # 무엇을 보나 → 2장
    - conditions: [{evaluator: {type: gt, params: [0]}}]                 # 조건
```

- **`for`**: 저절로 복구될 수 있는 문제는 몇 분을 준다(그 시간을 버티면 사람이 볼 일이라는 뜻).
  사람이 손대야 없어지는 문제는 `0m`.
- **해제 알림**: 조건이 거짓이 되거나 그 시계열이 사라지면 같은 채널로 ✅ 해제 메시지가 나간다.
  Grafana가 보는 건 조건뿐이라, 사람이 진짜 고쳤는지는 모른다.

---

## 4. 알림 메시지

기본 템플릿은 값을 통째로 쏟아내서 정작 필요한 게 안 보인다. 그래서 항목을 직접 세웠다.
문구에 쓸 수 있는 재료는 셋뿐이다.

| 재료 | 무엇 |
|---|---|
| `.Labels.*` | 시계열 라벨 — `by (...)`에 넣은 것만 ([2장](#2-지표)) |
| `.Annotations.*` | 규칙에 적어둔 문구(사유·조치·확인 명령어·리드라이브 명령어·로그 쿼리·조회 SQL·복구 SQL). 평가 시점에 `$labels`·`$values`가 박혀 굳는다 |
| `.GeneratorURL` / `.SilenceURL` | 규칙 상세 / 무음(Silence) 화면 링크 |

한 메시지에 알림이 여러 개 담길 수 있다([5장](#5-그룹핑과-재발송)). 섞이지 않게 알림마다
**제목 → 내용 → 링크**를 한 덩어리로 쓰고, 덩어리 사이는 빈 줄로 나눈다.

- 링크는 그 알림의 것을 단다 — `알림 일시 중지`가 그 알림 하나만 잠재우기 때문이다.
- 줄바꿈 하나는 디스코드가 같은 문단으로 붙여 그리므로, 항목 사이는 빈 줄로 띄운다.
  단 코드 블록 뒤에는 디스코드가 여백을 이미 줘서 넣지 않는다 — 코드 블록이 연달아 오는 항목
  (확인 명령어 → 리드라이브 명령어)은 뒤쪽이 앞의 빈 줄을 `{{-`로 먹는다.
- **명령어는 조치 문장에 섞지 않고 항목으로 세운다.** 폰에서 길게 눌러 복사할 수 있고, 조치는 한 줄로 읽힌다.

---

## 5. 그룹핑과 재발송

| 항목 | 값 | 뜻 |
|---|---|---|
| `group_by` | `alertname, instance, queue, reason` | 넷이 같으면 한 그룹 = 메시지 하나 |
| `group_wait` | 30초 | 첫 발송 전 같은 그룹을 기다려 모은다 |
| `repeat_interval` | 4시간 | 안 고치면 4시간마다 한 번 더 |

---

## 6. 사용 디스코드 채널

| 채널 | 무엇을 받나 | 웹후크 |
|---|---|---|
| `messaging` | 이벤트가 제대로 전달되는가 — 전송 적체·전송 실패·처리 실패(DLQ) | `DISCORD_MESSAGING_WEBHOOK_URL` |
| `service` | 서비스가 살아 있는가 — 응답 없음, 나중에 CPU·메모리 | `DISCORD_SERVICE_WEBHOOK_URL` |

---

## 7. 사용 알림

`.docker/observability/grafana/alerting/alerts.yaml`에 있다.

| 알림 | 채널 | 조건 | 무슨 뜻인가 |
|---|---|---|---|
| 이벤트 전송 적체 | `messaging` | `max by (instance, queue) (modudrive_outbox_lag_seconds) > 120` · `for: 5m` | 이벤트가 SQS로 안 나가고 쌓인다 |
| 이벤트 전송 실패 | `messaging` | `max by (instance, queue, reason, detail) (modudrive_outbox_failed) > 0` | 사람이 손대야 하는 행이 있다 |
| 이벤트 처리 실패 | `messaging` | `max by (instance, queue) (modudrive_dlq_messages) > 0` | 컨슈머가 포기한 메시지가 DLQ에 있다 |
| 서비스 응답 없음 | `service` | `min by (instance) (up{job="services"}) < 1` · `for: 2m` | 스크레이프 실패 = 프로세스가 죽었다 |

1. **이벤트 전송 적체** — SQS가 막히면 `PENDING` 행이 나가지 못하고 가장 오래된 행의 나이가 계속 커진다.
120초를 5분간 넘기면 알린다. 왜 막혔는지는 지표에 없으니(relay는 일시적 실패를 로그로만 남긴다),
그 로그로 가는 LogQL을 알림에 같이 실어 보낸다.

2. **이벤트 전송 실패** — `FAILED` 행은 사람이 손대기 전까지 사라지지 않으니 한 건이라도 생기면 바로 알린다.
큐·사유별로 쪼개 보내고, 그 라벨이 박힌 조회·복구 SQL이 알림에 같이 간다.

3. **이벤트 처리 실패** — 컨슈머가 포기한 메시지는 `<큐>-dlq`로 옮겨지고, 사람이 redrive하기 전까지 거기 남는다
([005-messaging-spec.md 4-2](005-messaging-spec.md#4-2-처리-실패)). 컨슈머가 30초마다 DLQ 건수를 재서 내보내고, 한 건이라도
있으면 바로 알린다. 사유는 지표에 못 담으니 DLQ 메시지의 `DeadLetterReason`을 보고 고친 뒤 redrive한다.

4. **서비스 응답 없음** — Prometheus가 15초마다 긁는 액추에이터가 2분 내내 응답하지 않으면 알린다.
프로세스가 죽었거나 액추에이터가 막힌 경우다. 2분을 주는 건 재배포로 잠깐 내려가는 것까지 알리지 않기 위해서다.

---

## 8. TODO

- [ ] **AWS로 가면**: 세 지표 모두 앱이 내보내는 커스텀 메트릭이라 CloudWatch가 저절로 알지 못한다.
  OTel Collector에 CloudWatch EMF exporter를 붙이거나 ADOT를 쓰는 선택이 남아 있다 —
  [aws-migration.md 2-10](../aws-migration.md#2-10--모니터링--알림)의 모니터링 항목과 같이 정한다. DLQ 건수만은 예외로
  CloudWatch가 `ApproximateNumberOfMessagesVisible`을 이미 알고 있으므로, Terraform으로 큐를 만들 때
  CloudWatch Alarm을 같이 걸어 앱 폴링을 걷어내는 선택지도 있다.
