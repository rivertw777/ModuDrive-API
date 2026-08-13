# 파일 공유(Sharing) 기능 재설계

## 0. 현재 상태 먼저 (설계 전 진단)

기존 코드를 보면 두 가지가 이미 있고, 하나가 빠져 있습니다.

- `FileShare` 도메인 + `file_share` 테이블 + `POST /files/{id}/share` 는 이미 존재 (`FileShare.java`, `ShareFileService.java`)
- `Permission{READ,WRITE}` 플랫 enum, 이메일이 아니라 `sharedWithUserId(UUID)`로 직접 초대
- **`ShareFileService`, `GetFileService`, `RenameFileService` 등 어디에도 호출자가 OWNER인지, 공유받은 사람인지 검증하는 코드가 없습니다.** `X_USER_ID`만 있으면 파일 소유자가 아니어도 `GET/RENAME/DELETE`가 그대로 통과합니다. 헤더 자체는 게이트웨이가 위조를 막아주지만(`UserContextFilter`가 클라이언트가 보낸 헤더를 지우고 JWT로 재계산), 그 뒤 **"이 사용자가 이 파일에 접근 권한이 있는가"는 파일 서비스 어디서도 확인하지 않습니다.**

→ 이번 재설계는 신규 기능 추가이자 동시에 이 권한 검증 공백을 메우는 작업입니다. 아래 설계는 이 전제를 깔고 갑니다.

---

## 1. 권한(Role) 모델 — 계층형 enum, 용량 플래그 테이블 아님

```java
public enum Role {
    VIEWER,
    EDITOR;   // ordinal 순서 = 포함 관계. EDITOR ⊇ VIEWER
}
```

- **OWNER는 저장하지 않음.** `File.ownerId == callerId`로 판별. `file_share`에 OWNER 행을 만들지 않는 이유: OWNER는 파일이 존재하는 한 항상 유일하고 이미 `file.owner_id`에 있는 정보라, 별도 행을 만들면 "OWNER를 바꾸는 것"과 "OWNER를 공유 목록에서 지우는 것"이라는 있어선 안 될 상태가 생김.
- `Role`은 **ordinal 기반 상하관계**로 취급 (`role.ordinal() >= required.ordinal()`). "파일 내용 수정"이 나중에 추가되면 `EDITOR`에 capability 메서드 하나(`canEditContent()`) 추가로 끝 — 새 role도, 스키마 변경도 필요 없음. 요구사항에 이미 "Editor가 Viewer를 포함한다"고 명시돼 있어서 딱 이 모델에 맞습니다.
- **트레이드오프**: 이 방식은 role들이 서로 완전 포함관계(nested)일 때만 성립합니다. 나중에 "Commenter"처럼 Viewer와 겹치지 않는 독립 role이 생기면 ordinal 비교가 깨짐 — 그때는 role→capability Set 매핑으로 갈아타야 함. 지금은 2단계 nested 요구사항만 있으므로 capability 테이블은 오버엔지니어링.

---

## 2. 공유 범위(Scope)

```java
public enum ShareScope {
    RESTRICTED,  // 권한이 있는 사용자만
    LINK         // 링크가 있는 모든 사용자 (Viewer 수준 고정)
}
```

- `File` 애그리거트에 컬럼 2개 추가: `access_scope`, `link_token`. 별도 테이블(`FileShareSetting` 1:1)로 뺄 수도 있지만, 카디널리티가 1:1이고 컬럼이 2개뿐이라 조인 하나 더 만드는 비용이 안 나옴 → File에 직접.
- 링크로 들어온 사용자는 요구사항상 "조회/다운로드"만 되므로 role을 따로 저장하지 않고 **Viewer 캐퍼빌리티로 고정**. "링크 사용자도 편집 가능"이 나중에 필요해지면 그때 `link_role` 컬럼 한 줄 추가.
- **토큰**: `UUID.randomUUID()` — 122비트 엔트로피, 이미 unique/indexed 컬럼으로 충분히 "유추 불가능"함. HMAC 서명 URL 방식도 가능하지만 그건 "DB 조회 없이 검증"이 목적인데, 이 API는 어차피 파일 조회를 위해 DB를 때려야 하므로 서명 검증으로 절약되는 게 없음 — UUID+DB lookup이 더 단순하고 동등하게 안전.
- **확장 지점**: 조직 전체 공개, 특정 그룹 공유는 `ShareScope`에 `ORG`, `GROUP` 값을 추가하고 `scope_target_id` 컬럼(nullable)을 얹으면 됨. 지금 만들지 않음 — 요구사항에도 "늘어날 가능성"이라고만 돼 있어 실제 니즈 없이 미리 만들 이유 없음.

---

## 3. 알림/메일 발송 디커플링

레포 전체에 `ApplicationEventPublisher`/`@EventListener` 사용례가 **전혀 없어서** 이번이 첫 도입입니다. 새 메시지 큐를 들이는 대신 Spring이 이미 제공하는 기능만 사용:

```java
public record FileShareInvitedEvent(UUID fileId, UUID granterId, UUID granteeId, Role role) {}
```

- `ShareFileService`는 `FileShare` 저장 후 `eventPublisher.publishEvent(new FileShareInvitedEvent(...))`만 호출.
- 리스너는 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 등록 — 커밋 실패 시 이벤트가 안 나가게 해서 "공유는 롤백됐는데 메일은 나갔다" 같은 불일치를 막음.
- 지금은 리스너를 로그만 찍는 스텁으로 둠(메일 발송 요구사항이 "지금 당장 아님"이라 명시됨).

**트레이드오프**: 이건 프로세스 내 디커플링일 뿐, file-service가 죽으면 이벤트도 같이 사라짐. notification-service가 이미 별도 서비스로 존재하니, 나중에 리스너가 Feign으로 notification-service를 호출하는 식으로 바꾸면 됨(auth→member 패턴과 동일). 진짜 안 죽는 배달 보장이 필요해지면 그때 아웃박스/큐로 업그레이드 — 지금 미리 큐를 놓는 건 컨슈머가 하나뿐인 시점에 과함.

---

## 4. 데이터 모델

```
file (기존 테이블, 컬럼 추가)
├─ ... 기존 컬럼
├─ access_scope   VARCHAR NOT NULL DEFAULT 'RESTRICTED'   -- RESTRICTED | LINK
└─ link_token     UUID    NULL UNIQUE                     -- scope=LINK일 때만 값 존재

file_share (기존 테이블, 의미 변경)
├─ id                UUID PK
├─ file_id           UUID NOT NULL
├─ owner_id          UUID NOT NULL
├─ shared_with_user_id UUID NOT NULL
├─ role              VARCHAR NOT NULL   -- 기존 permission(READ/WRITE) → VIEWER/EDITOR로 rename
├─ created_at        TIMESTAMP
└─ UNIQUE (file_id, shared_with_user_id)   -- ★현재 앱 레이어 existsBy 체크만 있고 DB 유니크 제약이 없음.
                                              동시 요청 두 개가 겹치면 중복 초대가 뚫릴 수 있는 TOCTOU 버그.
                                              이번에 제약 추가 권장.

member-service (신규)
└─ GET /api/v1/member/find-by-email?email=...
   → FindMemberPort.findMemberByEmail(...)는 이미 있음(로그인에서만 씀). 이걸 재사용해
     별도 유스케이스+컨트롤러만 추가하면 됨. common/api에 MemberResponse{id,name,email} DTO 신설
     (현재 common/api엔 auth 전용 DTO만 있고 범용 멤버 조회 DTO가 없음).
```

file-service는 이미 `common:infrastructure:spring-cloud`에 의존하므로(Feign/Eureka 이미 classpath에 있음) auth-service의 `MemberClient`와 똑같은 패턴으로 file-service→member-service Feign 클라이언트를 하나 추가하면 이메일 초대가 가능해집니다.

이 프로젝트는 Flyway/Liquibase 없이 `SPRING_JPA_HIBERNATE_DDL_AUTO=update`로 스키마를 관리하므로, 엔티티 필드만 추가하면 컬럼/제약이 dev DB에 자동 반영됩니다(별도 마이그레이션 스크립트 불필요).

---

## 5. API 설계

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/v1/files/{fileId}/shares` | OWNER only | body `{email, role}` — 이메일로 멤버 조회 후 FileShare 생성, `FileShareInvitedEvent` 발행 |
| `GET` | `/api/v1/files/{fileId}/shares` | OWNER only | OWNER 포함 현재 접근자 목록 + scope 정보 반환 (2.2 화면용) |
| `PATCH` | `/api/v1/files/{fileId}/shares/{shareId}` | OWNER only | body `{role}` — 권한 변경 |
| `DELETE` | `/api/v1/files/{fileId}/shares/{shareId}` | OWNER only | 제거 |
| `PUT` | `/api/v1/files/{fileId}/scope` | OWNER only | body `{scope}` — RESTRICTED↔LINK 전환, LINK 전환 시 `link_token` 발급 |
| `GET` | `/api/v1/files/public/{token}` | **인증 불필요** (gateway `permitAll`, 기존 `/member/sign-up`과 같은 패턴) | scope=LINK && token 일치 시 읽기 전용 상세 뷰 반환 |
| `GET` | `/api/v1/files/{fileId}` 등 기존 엔드포인트 | 로그인 필요 | **신규**: `FileAccessGuard`로 OWNER/공유 role 확인 후 통과 (현재 공백 메움) |

`link_token` 재발급(회전) 엔드포인트는 요구사항에 명시적으로 없어서 뺐습니다 — 필요해지면 `PUT .../scope`에 `regenerate: true` 하나 얹으면 됩니다.

---

## 6. 프론트엔드 컴포넌트 구조 (제안)

이 레포는 백엔드(API) 전용이라 실제 코드는 없고, 설계안만 제시합니다.

```
ShareModal
├─ AccessScopeSelect        # RESTRICTED/LINK — ShareScope enum 값을 그대로 옵션으로 매핑,
│                             제네릭 <Select> 프리미티브 위에 얇게 얹음
├─ MemberAccessList
│   └─ MemberAccessRow      # OWNER 행은 role 표시만(수정 불가), 나머지는 RoleSelect + 제거 버튼
├─ AddMemberForm
│   ├─ EmailInput
│   ├─ RoleSelect           # MemberAccessRow와 "같은 컴포넌트" 재사용 — Role enum에 값 추가되면
│   │                         한 군데만 고치면 양쪽 다 반영됨
│   └─ AddButton            # email && role 모두 채워질 때만 활성화
└─ LinkPanel                # scope=LINK일 때만 렌더, 링크 복사 버튼

PublicFileView (별도 최상위 라우트, 비로그인 접근)
└─ ReadOnlyFilePreview      # ShareModal 쪽 컴포넌트와 공유하지 않음 — 인증 경로가 다르므로
                              트리를 분리해야 "로그인 안 한 사용자가 실수로 관리 UI를 보는" 사고를 원천 차단
```

재사용 포인트는 `RoleSelect` 하나(초대 폼/기존 목록 양쪽에서 씀)와, 그 아래의 범용 `Select` 프리미티브뿐입니다. `AccessScopeSelect`도 같은 프리미티브를 씀 — 새 Select 컴포넌트를 매번 만들지 않는 게 핵심 재사용성 근거입니다.

---

## 7. 트레이드오프 요약

| 결정 | 선택 | 안 한 것 / 이유 |
|---|---|---|
| 권한 모델 | ordinal enum(VIEWER<EDITOR) | capability flag 테이블 — 지금은 nested 2단계뿐이라 과함. role이 비-nested가 되는 순간 깨짐(그때 전환) |
| Scope 저장 위치 | `file` 테이블에 컬럼 추가 | 별도 1:1 테이블 — 조인 비용만 늘고 얻는 게 없음 |
| 링크 토큰 | `UUID` + DB lookup | 서명 URL(무상태 검증) — 이 API는 어차피 DB를 치므로 서명 검증으로 아낄 게 없음 |
| 알림 디커플링 | 인프로세스 Spring 이벤트 | 메시지 큐 — 컨슈머 1개인 지금 단계에서 큐는 조로 과함. 안 죽는 배달이 필요해지면 그때 도입 |
| 기존 엔드포인트 권한 공백 | 재설계 범위에 포함 | 신규 기능만 추가 — 이러면 "공유 안 한 사람이 여전히 아무 파일이나 GET 가능"이라는 보안 구멍이 재설계 후에도 남음 |

---

## 구현 범위 결정

옵션 2: 신규 공유 기능 + 기존 엔드포인트(Get/Rename/Delete/Move 등) 권한 검증까지 전부 구현.
