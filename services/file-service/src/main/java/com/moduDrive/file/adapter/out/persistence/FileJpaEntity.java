package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.BaseTimeEntity;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
// Constrained on (namespace_id, path, active_slot_name) rather than plain `name`: a unique index
// treats NULLs as distinct from one another (standard SQL/Postgres behavior), so any number of
// TRASHED/DELETED rows — active_slot_name always NULL for those, see activeSlotName() below — can
// share a namespace/path/name with each other and with one live row. A trashed file therefore
// never blocks (or gets silently resurrected by) a fresh upload at its old name; only two *active*
// rows at the same slot collide.
@Table(name = "file", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_namespace_path_active_name", columnNames = {"namespace_id", "path", "active_slot_name"})
}, indexes = {
        // Backs the trash-retention sweep (findExpiredTrash: status + trashed_at), which scans
        // every namespace — without this it's a full table scan every night.
        @Index(name = "ix_file_status_trashed_at", columnList = "status, trashed_at")
})
@Entity
class FileJpaEntity extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private UUID namespaceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private UUID ownerId;

    private UUID currentVersionId;

    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileStatus status;

    // Column is `is_directory`; the Java side stays `directory` / `isDirectory()`.
    @Column(name = "is_directory", nullable = false)
    private boolean directory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareScope accessScope;

    /** Null while the file is RESTRICTED. */
    @Enumerated(EnumType.STRING)
    private Role linkRole;

    /** {@code name}, mirrored — except NULL while {@code status} is TRASHED or DELETED. Exists
     * purely to give {@code uk_file_namespace_path_active_name} something that goes NULL on
     * soft-delete; never read from Java, never exposed on the domain model. */
    @Column(name = "active_slot_name")
    private String activeSlotName;

    /** Set when the file is sent to trash, cleared on restore. Distinct from {@code BaseTimeEntity}'s
     * {@code deletedAt}, which this project only stamps on a *purge* (tombstone) — see
     * {@code SpringDataFileRepository.markPurged}. */
    private LocalDateTime trashedAt;

    FileJpaEntity(UUID namespaceId, String name, String path, UUID ownerId, FileStatus status, boolean directory) {
        this.namespaceId = namespaceId;
        this.name = name;
        this.path = path;
        this.ownerId = ownerId;
        this.status = status;
        this.directory = directory;
        this.accessScope = ShareScope.RESTRICTED;
        this.activeSlotName = activeSlotName(name, status);
    }

    void applyChanges(String name, String path, UUID currentVersionId, Long fileSize, FileStatus status,
                      ShareScope accessScope, Role linkRole, LocalDateTime trashedAt) {
        this.name = name;
        this.path = path;
        this.currentVersionId = currentVersionId;
        this.fileSize = fileSize;
        this.status = status;
        this.accessScope = accessScope;
        this.linkRole = linkRole;
        this.trashedAt = trashedAt;
        this.activeSlotName = activeSlotName(name, status);
    }

    private static String activeSlotName(String name, FileStatus status) {
        return FileStatus.REMOVED.contains(status) ? null : name;
    }
}
