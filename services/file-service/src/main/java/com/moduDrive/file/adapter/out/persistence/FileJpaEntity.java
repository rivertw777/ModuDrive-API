package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.BaseTimeEntity;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import org.hibernate.annotations.DynamicUpdate;
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
// Unique on active_slot_name, not name: it is NULL for TRASHED/DELETED rows, and NULLs never
// collide, so a trashed file doesn't block a new one at its old name. Only two active rows do.
@Table(name = "file", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_namespace_path_active_name", columnNames = {"namespace_id", "path", "active_slot_name"})
}, indexes = {
        // The trash-retention sweep (findExpiredTrash) across every namespace.
        @Index(name = "ix_file_status_trashed_at", columnList = "status, trashed_at")
})
@Entity
// Only the columns a save actually changed are written: saveFile copies every field over, and a
// full-row UPDATE would let a rename/move/share change put back the current version a commit set
// after this row was read.
@DynamicUpdate
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

    @Column(name = "is_directory", nullable = false)
    private boolean directory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareScope accessScope;

    /** Null while the file is RESTRICTED. */
    @Enumerated(EnumType.STRING)
    private Role linkRole;

    /** {@code name}, but NULL while TRASHED or DELETED — only for
     * {@code uk_file_namespace_path_active_name}. */
    @Column(name = "active_slot_name")
    private String activeSlotName;

    /** Set on trash, cleared on restore. {@code BaseTimeEntity.deletedAt} is the purge time
     * ({@code SpringDataFileRepository.markPurged}). */
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
