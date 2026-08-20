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
// The unique constraints (not just the app-layer existsBy checks) are what actually close the
// TOCTOU window where two concurrent invites for the same grantee both pass the check. Postgres
// treats each NULL as distinct, so the (file_id, shared_with_user_id) constraint never fires
// between two pending guest rows (both null), and the (file_id, granteeEmail) one never fires
// between two member-grant rows (both null) — each constraint only ever polices its own kind.
@Table(name = "file_share", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_share_file_user", columnNames = {"file_id", "shared_with_user_id"}),
        @UniqueConstraint(name = "uk_file_share_file_email", columnNames = {"file_id", "granteeEmail"}),
        @UniqueConstraint(name = "uk_file_share_token", columnNames = {"token"})
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

    /** Null for a pending guest share — see {@link com.moduDrive.file.domain.model.FileShare}. */
    private UUID sharedWithUserId;

    /** FK to {@code file_role.id}, resolved to/from a {@link com.moduDrive.file.domain.model.Role}
     * by {@link FileMapper} and {@link FilePersistenceAdapter} via the role directory cache.
     * Left DB-nullable on purpose, like {@code file.access_scope}/{@code link_role}: ddl-auto=update
     * can't add a NOT NULL column to a table that already has rows, so a pre-existing deployment's
     * rows would otherwise fail the migration outright. Application code always sets it on write. */
    private UUID grantedRoleId;

    /** Non-null only for a pending guest share: the per-invite capability token resolved by the
     * public routes (see {@code PublicFileResolver}). Null for a real member grant. */
    private UUID token;

    /** Non-null only for a pending guest share — the invited email. Null for a real member grant. */
    private String granteeEmail;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    FileShareJpaEntity(UUID fileId, UUID ownerId, UUID sharedWithUserId, UUID grantedRoleId) {
        this(fileId, ownerId, sharedWithUserId, grantedRoleId, null, null);
    }

    FileShareJpaEntity(UUID fileId, UUID ownerId, UUID sharedWithUserId, UUID grantedRoleId,
                       UUID token, String granteeEmail) {
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.sharedWithUserId = sharedWithUserId;
        this.grantedRoleId = grantedRoleId;
        this.token = token;
        this.granteeEmail = granteeEmail;
    }

    void applyGrantedRoleId(UUID grantedRoleId) {
        this.grantedRoleId = grantedRoleId;
    }

    /** Mirrors {@link com.moduDrive.file.domain.model.FileShare#claim}: fills {@code sharedWithUserId}
     * and clears the now-obsolete guest columns, so {@code token != null} keeps meaning "still a
     * pending guest share" everywhere it's checked (e.g. the scope-change revocation sweep). */
    void applyClaim(UUID sharedWithUserId) {
        this.sharedWithUserId = sharedWithUserId;
        this.token = null;
        this.granteeEmail = null;
    }
}
