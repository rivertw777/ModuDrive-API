# 파일 다운로드 스펙

이 문서는 파일 다운로드와 미리보기(인라인 보기)가 **현재 소스 기준으로** 어떻게 동작하는지 정의합니다.

⚠️ 이 문서가 기준입니다. 코드가 이 문서와 다르면 코드를 고치고, 동작을 바꾸려면 이 문서를 먼저 고칩니다.

> 기준 소스 (2026-09-23): API `dev` (851efb8), WEB `dev` (13ac387)
> - API: storage-service `StorageController`, `ArchiveController`, `PrepareArchiveService`, `OpenArchiveService`, `RedisArchiveTokenStore`, `DownloadFileService`, `PublicDownloadFileService`, `BlockAssembler`, `RedisDownloadQuotaStore`, `RedisStreamTokenStore`, `ResolveViewIdentityService`, `S3StorageAdapter` / file-service `GetLatestFileVersionsService`, `GetPublicFileRevisionsService`, `ResolveArchiveEntriesService`, `ResolvePublicArchiveEntriesService`, `ArchiveEntryCollector`, `FileAccessGuard`, `PublicFileResolver` / gateway `SecurityConfig`, `UserContextFilter`
> - WEB: `features/drive/api/download-file.ts`, `download-public-file.ts`, `download-archive.ts`, `view-file.ts`, `issue-stream-token.ts`, `components/file-preview.tsx`, `file-list.tsx`, `public-folder-view.tsx`, `types.ts`(`canPreviewFile`)

---

## 목차

- [1. 무엇을 받을 수 있는가](#1-무엇을-받을-수-있는가)
- [2. 전체 흐름](#2-전체-흐름)
- [3. 권한](#3-권한)
- [4. 어떤 버전이 내려가는가](#4-어떤-버전이-내려가는가)
- [5. 다운로드](#5-다운로드)
- [5-1. zip 다운로드 (여러 항목·폴더)](#5-1-zip-다운로드-여러-항목폴더)
- [6. 미리보기 (인라인 보기)](#6-미리보기-인라인-보기)
- [7. 다운로드 쿼터](#7-다운로드-쿼터)
- [8. 공개 링크에서의 다운로드](#8-공개-링크에서의-다운로드)
- [9. 제한값](#9-제한값)
- [10. API 요약](#10-api-요약)
- [11. 시나리오 검증](#11-시나리오-검증)
- [12. 알려진 문제](#12-알려진-문제)
- [13. TODO](#13-todo)

---

## 1. 무엇을 받을 수 있는가

| 선택 | 동작 |
|---|---|
| 파일 1개 | 원본 파일 그대로 받음 |
| 파일 여러 개 (다중 선택 → 우클릭 → `다운로드 (N개)`) | **zip 하나**로 받음 ([5-1장](#5-1-zip-다운로드-여러-항목폴더)) |
| 폴더 (우클릭 → `다운로드`) | 폴더째 **zip**으로 받음. 하위 폴더 구조와 빈 폴더까지 유지 |
| 파일 + 폴더 섞어서 | **zip 하나**로 받음 |
| `PENDING` 파일 | 메뉴에서 빠짐 (WEB이 `status === 'UPLOADED'`인 파일과 폴더만 다운로드 대상으로 봄). 폴더 안의 한 번도 완료되지 않은 파일은 zip에서 빠짐 |

## 2. 전체 흐름

```
클라이언트 ──▶ gateway ──▶ storage-service ──(Feign)──▶ file-service  : 권한 확인 + 최신 버전 위치(s3Path, blockCount)
                                │
                                ├─▶ Redis : 쿼터 확인
                                ├─▶ S3    : 블록 0..N-1을 순서대로 get → 복호화 → 압축 해제
                                └─▶ Redis : 실제로 내려간 바이트 기록
```

- 권한 판단은 **file-service가**, 바이트 조립은 **storage-service가** 합니다. storage-service는 file-service가 돌려준 위치만 읽습니다.
- storage-service가 file-service를 부르는 경로:
  - 로그인 사용자: `GET /internal/files/{fileId}/revisions?userId=&limit=1&markAccessed=`
  - 공개 링크: `GET /internal/files/public/{fileId}/revisions?key=&limit=1`

## 3. 권한

로그인 다운로드·미리보기는 `FileAccessGuard.requirePermission(file, userId, DOWNLOAD)`로 판단합니다.

| 호출자 | 결과 |
|---|---|
| 소유자 | 항상 허용 — **휴지통에 있는 파일도** 받을 수 있음 |
| 공유받은 사람 (VIEWER / EDITOR, 직접 또는 상위 폴더에서 상속) | 허용. 두 역할 모두 `DOWNLOAD` 권한이 있음 |
| 링크 공유(`LINK`) 범위 안의 로그인 사용자 | 허용 (VIEWER와 같은 권한) |
| 소유자가 아닌데 파일이 휴지통·삭제 상태 | `403 FILE_ACCESS_DENIED` |
| 그 외 | `403 FILE_ACCESS_DENIED` |
| 파일 없음 | `404 FILE_NOT_FOUND` |

- 공유 권한 계산(상속, 우선순위, 링크 폴백)은 [003-file-sharing-spec.md](003-file-sharing-spec.md)을 따릅니다.
- **"뷰어는 다운로드 금지" 같은 설정은 없습니다.** 볼 수 있으면 받을 수 있습니다.
- gateway는 들어오는 요청의 `X_USER_ID`를 항상 지우고, 인증된 요청에만 다시 넣습니다 (`UserContextFilter`). 그래서 permitAll 경로에서도 클라이언트가 `X_USER_ID`를 위조할 수 없습니다.

## 4. 어떤 버전이 내려가는가

- `file_version`을 **만든 시각 내림차순으로 1개**, 즉 가장 최근에 완료된 버전이 내려갑니다.
- 대체 업로드가 진행 중이면(`PENDING`) 새 버전이 아직 없으므로 **이전 버전**이 내려갑니다.
- 한 번도 완료되지 않은 파일은 버전이 없어 `404 FILE_NOT_FOUND_IN_STORAGE`가 납니다.
- **과거 버전을 골라 받는 기능은 없습니다.** 버전 이력은 `GET /api/v1/files/{fileId}/revisions`로 목록 조회만 가능합니다.

## 5. 다운로드

`GET /api/v1/storage/download/{fileId}` (로그인 필요)

- **서버**: S3에서 블록을 하나씩 가져와 복호화·압축 해제한 뒤, 바로 응답 스트림에 씁니다 (`StreamingResponseBody`). 파일 전체를 서버 메모리에 올리지 않으므로 크기 상한이 없습니다.
- **응답 헤더**:
  - `Content-Type: application/octet-stream`
  - `Content-Disposition: attachment; filename="{fileId}"` — 파일 이름이 아니라 **fileId**
  - `Content-Length` 없음
- **WEB**: axios로 `responseType: 'blob'` 요청 → 응답 전체를 **브라우저 메모리에 Blob으로** 받은 뒤, `<a download="{파일명}">`을 만들어 저장합니다.
  - 파일 이름은 WEB이 목록에서 알고 있는 이름을 씁니다.
  - 전부 받을 때까지 브라우저의 다운로드 진행 표시가 뜨지 않습니다.
  - 인증은 `Authorization: Bearer` 헤더로 합니다.
- 다운로드는 **최근 문서함에 기록하지 않습니다** (`markAccessed=false`).
- 전송 도중 S3 오류나 연결 끊김이 생기면 응답이 중간에 끊기고, WEB의 Blob 요청이 실패합니다. 이어받기는 없습니다.

## 5-1. zip 다운로드 (여러 항목·폴더)

파일 2개 이상, 또는 폴더가 하나라도 섞인 선택은 zip 하나로 받습니다. 두 단계로 동작합니다.

```
WEB ──POST /api/v1/storage/archive {fileIds}──▶ storage ──▶ file-service /internal/files/archive
                                                │            (권한 확인 + zip 구성 계산)
                                                ├─▶ 상한·쿼터 확인
    ◀──────────── { token } ────────────────────┘─▶ Redis archive-token:{uuid} (1분, 1회용)
WEB ──<a href> 링크 이동: GET /api/v1/storage/public/archive/{token}──▶ storage
                                                ├─▶ 토큰 소비 → file-service에 구성 다시 요청
    ◀──── application/zip 스트리밍 ─────────────┘─▶ S3 블록을 파일마다 순서대로 → zip에 씀
```

- **준비 요청**이 권한·상한·쿼터를 먼저 확인하므로, 실패는 JSON 에러로 돌아오고 WEB이 `alert`로 메시지를 보여줍니다. 브라우저 다운로드가 깨진 채 시작되지 않습니다.
- **링크 이동**이라 브라우저 기본 다운로드 진행 표시가 뜨고, zip 전체를 메모리에 올리지 않습니다 (단일 파일 다운로드의 Blob 방식과 다름 — 12장 1번).
- 링크에는 Authorization 헤더를 붙일 수 없으므로 **토큰이 곧 자격**입니다. 그래서 로그인·공개 모두 받는 경로는 permitAll인 `/api/v1/storage/public/archive/{token}` 하나입니다.

### 권한

- **직접 고른 항목마다** 3장과 같은 `DOWNLOAD` 권한을 확인합니다. 하나라도 없으면 **zip 전체가 실패**합니다 (403 / 404). 고른 걸 조용히 빼지 않습니다.
- 폴더 안의 하위 항목은 따로 확인하지 않습니다. 폴더 권한은 하위 전체에 상속되고, 하위의 개별 공유(VIEWER/EDITOR)도 모두 `DOWNLOAD`를 가지므로 결과가 같습니다.
- 휴지통·삭제된 하위 항목은 빠집니다.
- 토큰을 쓸 때 구성을 **다시** 계산합니다. 준비와 받기 사이(1분 안)에 권한이 사라지면 받기 요청이 에러로 끝납니다.

### zip 구성

| 규칙 | 예 |
|---|---|
| 고른 파일은 zip 최상위에 | `보고서.pdf` |
| 고른 폴더는 폴더 이름 아래에 하위 전체 | `사진/2024/a.jpg` |
| 빈 폴더도 항목으로 들어감 | `사진/빈폴더/` |
| 최상위 이름이 겹치면 번호 (파일은 확장자 앞) | `보고서.pdf`, `보고서 (1).pdf`, `docs (1)/` |
| 폴더 하나 또는 파일 하나만 고르면 zip 이름은 그 이름 | `사진.zip` |
| 여러 개를 고르면 시각 이름 (서버 시각, UTC) | `ModuDrive-20260923-142422.zip` |

- 파일명은 UTF-8 (zip의 UTF-8 플래그 설정), 4GB나 65,535개를 넘으면 ZIP64로 자동 전환됩니다 (`ZipOutputStream`).
- 압축 수준은 가장 빠른 단계 (`BEST_SPEED`) — 사진·영상처럼 이미 압축된 파일이 대부분이라 CPU만 쓰고 줄어드는 양이 적기 때문입니다.
- 응답: `Content-Type: application/zip`, `Content-Disposition: attachment; filename*=UTF-8''...`, `Cache-Control: private, no-store`, `Content-Length` 없음.
- 폴더와 **그 안의** 항목을 함께 고르면 안쪽 항목은 빠집니다 (폴더에 이미 들어 있으므로). 같은 바이트가 두 번 들어가거나 쿼터가 두 번 쌓이지 않습니다.

### 제한

- 고를 수 있는 항목 1,000개 (폴더는 1개로 셈).
- 폴더를 펼친 뒤 **파일 10,000개 / 합계 20GB** 초과면 `413 ARCHIVE_TOO_LARGE`. file-service가 고른 항목을 하나 펼칠 때마다 확인해서 넘는 즉시 멈추고, 준비·받기 두 번 모두 확인합니다.
- 쿼터는 파일마다 7장의 카운터를 그대로 씁니다. 준비 때 **파일 하나라도** 한도를 넘었으면 `429 DOWNLOAD_QUOTA_EXCEEDED`, 받는 동안에는 파일마다 실제로 zip에 쓴 (압축 전) 바이트를 기록합니다.
- 토큰이 없거나, 이미 썼거나, 1분이 지나면 `404 ARCHIVE_TOKEN_INVALID`.
- 전송 도중 오류가 나면 zip이 잘린 채 끝납니다 (12장 6번과 같음).
- 받기(GET) 단계가 실패하면(토큰 만료, 그 사이 권한 회수) 응답이 attachment가 아니라 JSON이라 탭이 에러 JSON 화면으로 넘어갑니다. 준비 직후 바로 링크를 따라가므로 드문 경우로 보고 두었습니다.

## 6. 미리보기 (인라인 보기)

`GET /api/v1/storage/view/{fileId}?fileName=&streamToken=` (gateway permitAll — 신원은 아래 두 가지 중 하나로 확인)

### 신원 확인

| 방식 | 쓰는 곳 | 설명 |
|---|---|---|
| `Authorization: Bearer` (gateway가 `X_USER_ID` 주입) | 텍스트·이미지 | WEB이 Blob으로 받아서 표시 |
| `streamToken` 쿼리 | 오디오·비디오 | `<video>`/`<audio>`는 헤더를 붙일 수 없으므로, WEB이 먼저 `POST /api/v1/storage/stream-token?fileId=`로 토큰을 받아 URL에 넣음 |

- 둘 다 있으면 헤더가 우선입니다. 둘 다 없거나 토큰이 틀리면 `401 UNAUTHENTICATED_VIEW_REQUEST`.
- 스트림 토큰의 규칙:
  - Redis `stream-token:{uuid}` → `{fileId}:{userId}`, **유효 30분**
  - **여러 번 쓸 수 있음** (시킹마다 같은 토큰을 재사용)
  - 발급한 파일에만 유효하고, 다른 fileId로 쓰면 401
  - 발급 시점에는 권한을 확인하지 않고, **보기 요청 때마다** 3장 권한으로 확인합니다

### 동작

- 권한과 버전 판단은 다운로드와 같습니다. 단, 미리보기는 **최근 문서함에 기록합니다** (`markAccessed=true`, 로그인 사용자만).
- **서버가 파일 전체를 메모리에 조립한 뒤** 응답합니다 (`byte[]`).
  - 조립 전에 `blockCount × STORAGE_BLOCK_SIZE(4MB) > 100MB`이면 `PREVIEW_TOO_LARGE`로 거절합니다.
- 응답 헤더:
  - `Content-Type`: `fileName` 확장자로 결정 (`FileMimeTypes`). `fileName`은 표시용일 뿐, 저장소를 조회하는 데는 쓰지 않습니다.
  - `Content-Disposition: inline`
  - `Accept-Ranges: bytes`
  - `Cache-Control: private, no-store`
  - `X-Content-Type-Options: nosniff`
- `Range` 헤더를 지원합니다.
  - 범위가 **1개**면 `206`과 해당 구간을 보냅니다. 전체를 조립한 뒤 배열을 잘라서 보냅니다.
  - 범위가 여러 개이거나, 형식이 틀리거나, 시작점이 파일 크기를 넘으면 **범위를 무시하고 전체**(200)를 보냅니다.

### WEB이 미리보기를 띄우는 조건 (`canPreviewFile`)

| 종류 | 확장자 | 크기 상한 (WEB) | 방식 |
|---|---|---|---|
| text | `txt`, `md` (HTML로 렌더링하지 않고 `<pre>`로 표시) | 10MB | Blob |
| image | IMAGE 분류 (**svg 제외** — 스크립트 실행 위험) | 10MB | Blob |
| audio | `mp3`, `wav`, `flac`, `aac`, `m4a` | 없음 (서버 100MB 추정 상한만) | `src`에 직접 URL + streamToken |
| video | VIDEO 분류 | 없음 (서버 100MB 추정 상한만) | 같음 |
| 그 외 | — | — | 미리보기 없음, 다운로드 버튼만 |

## 7. 다운로드 쿼터

같은 파일을 짧은 시간에 과도하게 받는 것을 막습니다 (구글 드라이브의 "다운로드 한도 초과"와 같은 개념).

- 카운터 키는 `download-quota:{scope}:{s3Path}`입니다.
  - 로그인: `scope` = userId → **사용자 × 파일 버전**마다 카운터
  - 공개 링크: `scope` = `public:{fileId}` → 익명 방문자 전원이 **파일 버전 하나의 카운터를 함께** 씀. `key`는 쓰지 않음 — 임의의 `key`로 새 카운터를 만들 수 없게 하기 위함
- 한도는 `STORAGE_DOWNLOAD_QUOTA_PER_FILE_BYTES`(기본 **10GB**), 창은 `STORAGE_DOWNLOAD_QUOTA_WINDOW`(기본 **24시간**)입니다.
  - 창은 첫 요청 때 시작되고, 24시간 뒤 카운터가 사라집니다 (고정 창, 슬라이딩 아님).
- 판단은 **요청 전에** 합니다. 이미 쓴 양 ≥ 한도이면 `DOWNLOAD_QUOTA_EXCEEDED`. 쓴 양이 한도 미만이면 파일이 아무리 커도 이번 요청은 통과합니다.
- 기록은 **실제로 내려간 바이트**로 합니다.
  - 다운로드: 스트림에 쓴 바이트 수 (중간에 끊겨도 그만큼만)
  - 미리보기: 조립한 **파일 전체 크기** (Range로 일부만 보내도)
- 한도가 0 이하이면 쿼터를 끕니다.
- Redis 장애 시 확인·기록을 **건너뛰고** 다운로드를 허용합니다 (보조 통제이므로 다운로드를 막지 않음).

## 8. 공개 링크에서의 다운로드

| 메서드 · 경로 | 용도 |
|---|---|
| `GET /api/v1/storage/public/{fileId}/download?key=` | 다운로드 (스트리밍) |
| `GET /api/v1/storage/public/{fileId}/view?key=&fileName=` | 미리보기 (`streamToken` 필요 없음 — URL 자체가 자격) |

- 두 경로 모두 gateway permitAll이고, WEB은 **토큰을 보내지 않습니다.** 만료된 토큰으로 로그아웃되는 일을 막기 위함입니다.
- 접근 판단은 file-service `PublicFileResolver`가 합니다 ([003-file-sharing-spec.md 4장](003-file-sharing-spec.md#4-링크-제공)).
  - 파일 자신이나 상위 폴더가 `LINK` 범위이면 `key` 없이 허용
  - 그렇지 않으면 게스트 초대 `key`가 맞아야 허용
  - 휴지통·삭제 항목은 제외
  - 허용되지 않으면 404
- 쿼터는 7장의 공개 카운터를 씁니다. 최근 문서함에는 기록하지 않습니다.
- zip 다운로드는 `POST /api/v1/storage/public/archive?key=` → 같은 토큰 링크입니다 ([5-1장](#5-1-zip-다운로드-여러-항목폴더)). 고른 항목마다 `PublicFileResolver`로 판단하고, 하나라도 안 되면 404. 쿼터는 파일마다 공개 카운터에 기록합니다.
- 공개 폴더 화면: 폴더 행·우클릭·다중 선택 모두 다운로드 가능합니다. 지금 열어 둔 폴더 전체를 받는 상단 버튼은 두지 않습니다.

## 9. 제한값

| 항목 | 값 | 위치 |
|---|---|---|
| 다운로드 크기 상한 | 없음 (스트리밍) | — |
| 미리보기 서버 상한 | `blockCount × 4MB` ≤ 100MB (추정치) | `BlockAssembler.MAX_INLINE_PREVIEW_BYTES` |
| 미리보기 WEB 상한 | 텍스트·이미지 10MB | WEB `PREVIEW_MAX_BYTES` |
| 블록 수 상한 | 100,000 (넘으면 `TOO_MANY_BLOCKS`) | `S3StorageAdapter.MAX_BLOCK_COUNT` |
| 스트림 토큰 유효 기간 | 30분, 재사용 가능 | `RedisStreamTokenStore.TTL` |
| 파일별 다운로드 쿼터 | 24시간당 10GB | `STORAGE_DOWNLOAD_QUOTA_PER_FILE_BYTES`, `STORAGE_DOWNLOAD_QUOTA_WINDOW` |
| zip 고른 항목 수 | 1,000개 | `PrepareArchiveCommand` |
| zip 파일 수 / 합계 크기 | 10,000개 / 20GB | file-service `ArchiveEntryCollector.MAX_FILES`, `MAX_BYTES` |
| zip 토큰 유효 기간 | 1분, 1회용 | `RedisArchiveTokenStore.TTL` |

## 10. API 요약

| 메서드 · 경로 | 서비스 | 인증 | 용도 |
|---|---|---|---|
| `GET /api/v1/storage/download/{fileId}` | storage | Bearer | 다운로드 (스트리밍, attachment) |
| `POST /api/v1/storage/stream-token?fileId=` | storage | Bearer | 오디오·비디오용 스트림 토큰 발급 |
| `GET /api/v1/storage/view/{fileId}?fileName=&streamToken=` | storage | Bearer 또는 streamToken | 미리보기 (inline, Range) |
| `GET /api/v1/storage/public/{fileId}/download?key=` | storage | 없음 | 공개 다운로드 |
| `GET /api/v1/storage/public/{fileId}/view?key=&fileName=` | storage | 없음 | 공개 미리보기 |
| `POST /api/v1/storage/archive` `{fileIds}` | storage | Bearer | zip 준비 → `{token}` |
| `POST /api/v1/storage/public/archive?key=` `{fileIds}` | storage | 없음 | 공개 zip 준비 → `{token}` |
| `GET /api/v1/storage/public/archive/{token}` | storage | 토큰 | zip 스트리밍 (attachment) |
| `POST /internal/files/archive` `{userId, fileIds}` | file | 서비스 간 | 고른 항목 DOWNLOAD 확인 + zip 구성 |
| `POST /internal/files/public/archive` `{key, fileIds}` | file | 서비스 간 | 공개 판단 + zip 구성 |
| `GET /internal/files/{fileId}/revisions?userId=&limit=&markAccessed=` | file | 서비스 간 | DOWNLOAD 권한 확인 + 최신 버전 위치 |
| `GET /internal/files/public/{fileId}/revisions?key=&limit=` | file | 서비스 간 | 공개 접근 판단 + 최신 버전 위치 |

## 11. 시나리오 검증

| # | 시나리오 | 결과 |
|---|---|---|
| 1 | 소유자가 파일 1개 다운로드 | 서버는 스트리밍, WEB은 Blob으로 다 받은 뒤 원래 이름으로 저장. 최근 문서함 변화 없음 |
| 2 | 파일 3개 + 폴더 1개 선택 후 다운로드 | 메뉴에 `다운로드 (4개)`. `ModuDrive-{시각}.zip` 하나에 파일 3개 + 폴더(하위 포함)가 들어감 |
| 3 | VIEWER로 공유받은 파일 다운로드 | 허용 |
| 4 | 공유받은 파일을 소유자가 휴지통으로 보냄 → 받은 사람이 다운로드 | 403. 소유자 본인은 휴지통에서도 받을 수 있음 |
| 5 | 대체 업로드 진행 중인 파일 다운로드 | 이전 버전이 내려감 |
| 6 | 업로드가 끝나지 않은 새 파일 | WEB 메뉴에 다운로드가 없음. API를 직접 부르면 404 `FILE_NOT_FOUND_IN_STORAGE` |
| 7 | 로그인 사용자가 50MB 영상 미리보기 | 스트림 토큰 발급 → `<video src>`. 시킹할 때마다 Range 요청이 가고, **서버는 매번 50MB 전체를 조립** |
| 8 | 150MB 영상 미리보기 (간단 업로드로는 불가하므로 이어 올리기, 5MB 블록 30개) | 서버 추정 30 × 4MB = 120MB > 100MB → `PREVIEW_TOO_LARGE` |
| 9 | 같은 사용자가 3GB 파일을 24시간 안에 4번 다운로드 | 1·2·3번째 통과(누적 9GB), 4번째도 통과(요청 전 9GB < 10GB), 5번째는 `DOWNLOAD_QUOTA_EXCEEDED` |
| 10 | 비로그인 방문자가 링크 공유 파일 다운로드 | `key` 없이 허용. 공개 카운터에 기록 |
| 11 | 링크 공유 아닌 파일에 임의 `key`를 붙여 공개 다운로드 | 404 |
| 12 | 폴더 `사진`(하위에 빈 폴더, 파일 3개) 우클릭 → 다운로드 | `사진.zip`: `사진/`, `사진/빈폴더/`, `사진/…` 파일 3개. 파일마다 쿼터 기록 (로컬 확인) |
| 13 | 권한 없는 사용자가 남의 폴더 zip 준비 | 403, 토큰 발급 안 됨 (로컬 확인) |
| 14 | 받은 zip 링크를 한 번 더 열기 | 404 `ARCHIVE_TOKEN_INVALID` (로컬 확인) |
| 15 | 비로그인 방문자가 링크 공유 폴더 + 링크 공유 아닌 파일을 함께 zip | 404, 아무것도 받지 않음 (로컬 확인) |
| 16 | 폴더를 펼쳐 파일이 10,001개 | 413 `ARCHIVE_TOO_LARGE` |
| 17 | 15초가 넘게 걸리는 zip (gateway 서킷브레이커 TimeLimiter 15초) | 끊기지 않음 — TimeLimiter는 응답 시작까지만 잼 (로컬에서 500KB/s로 19초 확인) |

## 12. 알려진 문제

소스를 읽으며 확인한 것들입니다. 고칠지는 이슈로 따로 정합니다.

| # | 문제 | 영향 |
|---|---|---|
| 1 | WEB이 다운로드를 **Blob으로 메모리에 다 받은 뒤** 저장 | GB 단위 파일은 브라우저 메모리를 다 쓰거나 탭이 죽을 수 있음. 다 받을 때까지 진행 표시도 없음. 서버 스트리밍의 이점이 사라짐 |
| 2 | 응답 `Content-Disposition`의 파일명이 **fileId**. `Content-Length`도 없음 | 링크 이동 방식으로 바꾸면 파일명이 UUID로 저장되고, 브라우저가 남은 시간을 표시하지 못함. 1번을 고치려면 같이 고쳐야 함 |
| 3 | 미리보기가 요청마다 **파일 전체를 서버 메모리에 조립** (Range도 마찬가지) | 영상 시킹 한 번마다 최대 100MB 조립 + S3 전체 읽기. 동시 시청자가 많으면 힙 압박 |
| 4 | 미리보기 쿼터가 Range 요청에도 **파일 전체 크기**로 기록 | 영상을 여러 번 시킹하면 실제 전송량보다 훨씬 빨리 10GB 쿼터를 소진 |
| 5 | 미리보기 크기 추정이 `blockCount × 4MB`인데, 이어 올리기 파일의 블록은 5MB ([001-file-upload-spec.md 6장](001-file-upload-spec.md#6-저장-방식)) | 추정치가 실제보다 작게 나옴. 이어 올리기로 올린 파일은 최대 약 125MB까지 100MB 상한을 통과해 메모리에 조립될 수 있음 |
| 6 | 스트리밍 도중 오류가 나면 이미 200 헤더가 나간 뒤라, 클라이언트는 **잘린 파일**을 받을 수 있음 | `Content-Length`가 없어 브라우저가 잘린 걸 알아채지 못할 수 있음 (2번과 관련) |
| 7 | 과거 버전을 받을 수 없음 | 버전 이력이 목록 조회로만 쓰임 |

## 13. TODO

이전 버전의 이 문서가 목표로 적었던 설계입니다. 구현하게 되면 해당 장으로 옮깁니다.

- **단일 파일 다운로드를 링크 이동 방식으로 전환** (12장 1·2번): 토큰 링크 + `Content-Disposition`에 실제 파일명(RFC 5987 `filename*`) + `Content-Length`
- **미리보기 Range를 S3 블록 단위로 처리** (12장 3·4·5번): 요청 구간에 해당하는 블록만 읽고, 쿼터도 보낸 만큼만 기록
- **과거 버전 다운로드** (12장 7번)
