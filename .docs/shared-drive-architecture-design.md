# ModuDrive — Shared Drive Service Architecture Design

---

## 1. Overview

ModuDrive is a cloud storage platform that allows users to upload, download, and manage files from any device. Files can be shared with other users, and all devices stay in sync automatically.

### In Scope

- File upload (simple and resumable)
- File download
- Multi-device synchronization
- Revision history
- File sharing
- Notifications (on file add / edit / delete / share)

### Out of Scope

- Real-time collaborative document editing
- Office-style document viewer

### Non-Functional Requirements

- **Reliability**: No data loss under any failure scenario.
- **Sync latency**: Changes propagate to other devices with minimal delay.
- **Bandwidth efficiency**: Only modified blocks are transferred (delta sync).
- **Scalability**: Horizontally scalable at every layer.
- **High availability**: No single point of failure.

---

## 2. New Services

Three new microservices are added to the existing stack (`member-service`, `auth-service`, `gateway-service`, `eureka-server`).

| Service | Port | Responsibility |
|---|---|---|
| `file-service` | 10012 | File metadata, versioning, sharing, directory management |
| `storage-service` | 10013 | Block-level file storage — split, compress, encrypt, upload/download via S3/MinIO |
| `notification-service` | 10014 | Long-polling notification delivery |

All three services register with Eureka, are routed through the gateway, and follow the same hexagonal architecture conventions as existing services.

---

## 3. Database Schema — `file-service`

PostgreSQL is used to guarantee ACID compliance (strong consistency requirement: the same file must never appear differently across devices or users).

```sql
-- User root directory
namespace (
  namespace_id UUID PK,
  user_id      BIGINT NOT NULL UNIQUE,
  root_path    VARCHAR NOT NULL,
  created_at   TIMESTAMP,
  updated_at   TIMESTAMP
)

-- File latest state
file (
  file_id            UUID PK,
  namespace_id       UUID FK → namespace,
  name               VARCHAR NOT NULL,
  path               VARCHAR NOT NULL,
  owner_id           BIGINT NOT NULL,
  current_version_id UUID,
  file_size          BIGINT,
  status             VARCHAR NOT NULL,  -- PENDING | UPLOADED | DELETED
  is_directory       BOOLEAN DEFAULT FALSE,
  created_at         TIMESTAMP,
  updated_at         TIMESTAMP
)

-- Immutable version history (read-only records)
file_version (
  version_id   UUID PK,
  file_id      UUID FK → file,
  file_size    BIGINT,
  block_count  INT,
  s3_path      VARCHAR NOT NULL,
  created_at   TIMESTAMP
)

-- Block-level metadata (enables delta sync)
block (
  block_id     UUID PK,
  version_id   UUID FK → file_version,
  block_order  INT NOT NULL,
  block_hash   VARCHAR NOT NULL,
  block_size   INT,
  s3_key       VARCHAR NOT NULL
)

-- File sharing
file_share (
  share_id           UUID PK,
  file_id            UUID FK → file,
  owner_id           BIGINT NOT NULL,
  shared_with_user_id BIGINT NOT NULL,
  permission         VARCHAR NOT NULL,  -- READ | WRITE
  created_at         TIMESTAMP
)
```

> `file_version` records are append-only. Revisions must never be mutated.

---

## 4. Phase 1 — `file-service`

### 4.1 Domain Models

| Class | Description |
|---|---|
| `Namespace` | User root directory |
| `File` | File metadata with status |
| `FileVersion` | Immutable version snapshot |
| `Block` | Block hash reference |
| `FileShare` | Sharing record |
| `FileStatus` | `PENDING`, `UPLOADED`, `DELETED` |
| `Permission` | `READ`, `WRITE` |

### 4.2 Use Cases (Inbound Ports)

| UseCase | Trigger |
|---|---|
| `CreateNamespaceUseCase` | Called by `member-service` on signup |
| `UploadFileMetadataUseCase` | Client registers file metadata before upload; sets `PENDING` |
| `UpdateFileStatusUseCase` | Called by `storage-service` callback after blocks are stored |
| `GetFileUseCase` | Single file lookup |
| `ListDirectoryUseCase` | List files under a path |
| `GetFileRevisionsUseCase` | Revision history with limit |
| `DeleteFileUseCase` | Soft delete |
| `ShareFileUseCase` | Grant access to another user |
| `CreateDirectoryUseCase` | Create a folder node |

### 4.3 Outbound Ports

| Port | Target |
|---|---|
| `SaveFilePort` | Persistence (JPA) |
| `FindFilePort` | Persistence (JPA) |
| `SaveFileVersionPort` | Persistence (JPA) |
| `FindFileVersionsPort` | Persistence (JPA) |
| `SaveNamespacePort` | Persistence (JPA) |
| `FindNamespacePort` | Persistence (JPA) |
| `NotifyFileEventPort` | `notification-service` via WebClient |

### 4.4 REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/files/metadata` | Register file metadata (`PENDING`) |
| `PUT` | `/api/v1/files/{fileId}/uploaded` | Storage callback — mark `UPLOADED` |
| `GET` | `/api/v1/files/{fileId}` | Get file info |
| `DELETE` | `/api/v1/files/{fileId}` | Soft-delete file |
| `GET` | `/api/v1/files/{fileId}/revisions` | Revision history (`?limit=20`) |
| `POST` | `/api/v1/files/{fileId}/share` | Share file with another user |
| `GET` | `/api/v1/directories` | List directory contents (`?path=`) |
| `POST` | `/api/v1/directories` | Create directory |
| `POST` | `/api/v1/namespaces` | Create user namespace |

### 4.5 Upload Flow

```
Client ──[1. POST /files/metadata]──────────────────→ file-service
         file-service saves status=PENDING
         file-service → notification-service: FILE_UPLOAD_STARTED

Client ──[2. POST /storage/upload]──────────────────→ storage-service
         storage-service splits → compresses → encrypts → uploads blocks to S3/MinIO
         storage-service ──[3. PUT /files/{fileId}/uploaded]──→ file-service
         file-service updates status=UPLOADED
         file-service → notification-service: FILE_UPLOADED
         notification-service → other devices (long poll response)
```

### 4.6 Package Structure

```
services/file-service/src/main/java/com/moduDrive/file/
├── domain/
│   ├── model/       File, FileVersion, Block, Namespace, FileShare
│   └── vo/          FilePath, FileSize, BlockHash
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── usecase/   *UseCase.java
│   │   │   └── command/   *Command.java
│   │   └── out/           *Port.java
│   └── service/           *Service.java  (@UseCase)
├── adapter/
│   ├── in/web/
│   │   ├── controller/    *Controller.java  (@WebAdapter)
│   │   ├── dto/           *Request.java, *Response.java
│   │   └── mapper/        *ResponseMapper.java
│   └── out/
│       ├── persistence/   *JpaEntity, *PersistenceAdapter  (@PersistenceAdapter)
│       └── client/        NotificationClientAdapter, StorageCallbackAdapter
└── exception/             FileExceptionCase.java
```

---

## 5. Phase 2 — `storage-service`

### 5.1 Design Principles

- **Delta sync**: On update, only changed blocks are re-uploaded. Each block carries a SHA-256 hash; unchanged hashes are skipped.
- **Compression**: Text files use gzip; binary/media files use a format-appropriate algorithm.
- **Encryption**: Each block is encrypted before transfer to object storage.
- **Resumable upload**: For large files or unreliable networks, clients upload in chunks with a session token.

### 5.2 Use Cases

| UseCase | Description |
|---|---|
| `SimpleUploadUseCase` | Split → compress → encrypt → store; callback to `file-service` |
| `InitResumableUploadUseCase` | Create session, return `sessionId` |
| `UploadChunkUseCase` | Accept chunk, store block, track progress |
| `CompleteResumableUploadUseCase` | Merge chunks, finalize upload, callback to `file-service` |
| `DownloadFileUseCase` | Return presigned URL or stream blocks |

### 5.3 REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/storage/upload` | Simple upload (`multipart/form-data`) |
| `POST` | `/api/v1/storage/upload/resumable` | Initiate resumable upload → `{ sessionId }` |
| `PUT` | `/api/v1/storage/upload/resumable/{sessionId}` | Upload chunk |
| `POST` | `/api/v1/storage/upload/resumable/{sessionId}/complete` | Finalize resumable upload |
| `GET` | `/api/v1/storage/download/{fileId}` | Presigned URL or direct stream |

### 5.4 Object Storage

| Environment | Backend |
|---|---|
| Local / Dev | MinIO (`docker-compose.infra.yml`) |
| Production | AWS S3 |

SDK: `software.amazon.awssdk:s3` (AWS SDK v2) — same interface for both targets via endpoint override.

**MinIO addition to `docker-compose.infra.yml`:**

```yaml
minio:
  image: minio/minio:latest
  ports:
    - "9000:9000"
    - "9001:9001"
  environment:
    MINIO_ROOT_USER: minioadmin
    MINIO_ROOT_PASSWORD: minioadmin
  command: server /data --console-address ":9001"
```

---

## 6. Phase 3 — `notification-service`

### 6.1 Transport: Long Polling

Long polling is chosen over WebSocket because:
- Notification flow is **server → client only** (no need for bidirectional channel).
- Notification frequency is low; short bursts of data suffice.
- Simpler infrastructure, no stateful connection management.

### 6.2 Offline Queue

When a client is disconnected, notifications are stored in DB with `status=PENDING`.  
On reconnect, `PollNotificationsUseCase` detects pending notifications and returns them immediately instead of waiting.

### 6.3 Use Cases

| UseCase | Trigger |
|---|---|
| `PublishNotificationUseCase` | `file-service` calls this after every file event |
| `PollNotificationsUseCase` | Client long-polls; waits up to 30s, returns on first event |
| `GetNotificationsUseCase` | Paginated notification history |
| `MarkDeliveredUseCase` | Client confirms receipt |

### 6.4 Domain Models

| Class | Values |
|---|---|
| `NotificationType` | `FILE_ADDED`, `FILE_MODIFIED`, `FILE_DELETED`, `FILE_SHARED` |
| `NotificationStatus` | `PENDING`, `DELIVERED` |

### 6.5 REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/notifications` | (Internal) Publish event from `file-service` |
| `GET` | `/api/v1/notifications/poll` | Long-poll — blocks until event or 30s timeout |
| `GET` | `/api/v1/notifications` | Notification history (paginated) |
| `PUT` | `/api/v1/notifications/{id}/delivered` | Acknowledge delivery |

---

## 7. Phase 4 — Integration

### 7.1 Gateway Routes

Add to `RouteConfig.java`:

```java
.route("file-service", r -> r
    .path("/api/v1/files/**", "/api/v1/directories/**", "/api/v1/namespaces/**")
    .filters(f -> addCircuitBreaker(f, "fileServiceCircuitBreaker"))
    .uri("lb://file-service"))
.route("storage-service", r -> r
    .path("/api/v1/storage/**")
    .filters(f -> addCircuitBreaker(f, "storageServiceCircuitBreaker"))
    .uri("lb://storage-service"))
.route("notification-service", r -> r
    .path("/api/v1/notifications/**")
    .filters(f -> addCircuitBreaker(f, "notificationServiceCircuitBreaker"))
    .uri("lb://notification-service"))
```

### 7.2 `common:api` Shared DTOs

```
common/api/src/main/java/com/moduDrive/common/api/dto/
├── file/
│   ├── FileUploadCallbackRequest.java    # storage-service → file-service
│   ├── FileEventNotifyRequest.java       # file-service → notification-service
│   └── CreateNamespaceRequest.java       # member-service → file-service
└── notification/
    └── PublishNotificationRequest.java
```

### 7.3 `member-service` Integration

After successful signup, `SignUpMemberService` calls `POST /api/v1/namespaces` on `file-service` via Feign to create the user's root namespace automatically.

### 7.4 `settings.gradle`

```groovy
include("services:file-service")
include("services:storage-service")
include("services:notification-service")
```

### 7.5 Final Port Map

| Service | Port |
|---|---|
| eureka-server | 10000 |
| gateway-service | 10001 |
| member-service | 10010 |
| auth-service | 10011 |
| **file-service** | **10012** |
| **storage-service** | **10013** |
| **notification-service** | **10014** |

---

## 8. Implementation Order

```
Phase 1  file-service
         ├── Module scaffold + build.gradle
         ├── DB schema + JPA entities
         ├── Domain models (Namespace, File, FileVersion, Block)
         ├── Core use cases (UploadMetadata, UpdateStatus, GetFile, ListDir, GetRevisions)
         ├── REST controllers + PersistenceAdapters
         └── Tests (unit: service, integration: controller, ArchUnit)

Phase 2  storage-service
         ├── Module scaffold + MinIO infra
         ├── S3Client adapter (MinIO-compatible)
         ├── Simple upload use case (split → compress → encrypt → store)
         ├── Resumable upload use case (session-based chunks)
         ├── Download use case (presigned URL)
         └── Callback to file-service on complete

Phase 3  Integration
         ├── Gateway: add three new routes
         ├── member-service → file-service namespace creation on signup
         └── common:api DTO additions

Phase 4  notification-service
         ├── Module scaffold
         ├── Long-polling endpoint (DeferredResult / async servlet)
         ├── file-service → notification-service event wiring
         └── Offline queue (PENDING → flush on reconnect)
```
