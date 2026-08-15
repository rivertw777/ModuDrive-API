package com.moduDrive.file.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
// The unique constraint (not just the app-layer existsBy check) is what actually closes the
// TOCTOU window where two concurrent invites for the same (file, user) both pass the check.
@Table(name = "file_share", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_share_file_user", columnNames = {"file_id", "shared_with_user_id"})
})
@Entity
@EntityListeners(AuditingEntityListener.class)
class FileShareJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID fileId;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private UUID sharedWithUserId;

    /** FK to {@code file_role.id}, resolved to/from a {@link com.moduDrive.file.domain.model.Role}
     * by {@link FileMapper} and {@link FilePersistenceAdapter} via the role directory cache.
     * Left DB-nullable on purpose, like {@code file.access_scope}/{@code link_role}: ddl-auto=update
     * can't add a NOT NULL column to a table that already has rows, so a pre-existing deployment's
     * rows would otherwise fail the migration outright. Application code always sets it on write. */
    private UUID grantedRoleId;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    FileShareJpaEntity(UUID fileId, UUID ownerId, UUID sharedWithUserId, UUID grantedRoleId) {
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.sharedWithUserId = sharedWithUserId;
        this.grantedRoleId = grantedRoleId;
    }

    void applyGrantedRoleId(UUID grantedRoleId) {
        this.grantedRoleId = grantedRoleId;
    }
}
