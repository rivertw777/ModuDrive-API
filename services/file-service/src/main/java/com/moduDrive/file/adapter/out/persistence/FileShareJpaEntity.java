package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
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
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private UUID fileId;

    @Column(nullable = false)
    private UUID ownerId;

    /** Null for a pending guest share — see {@link com.moduDrive.file.domain.model.FileShare}. */
    private UUID sharedWithUserId;

    /** Left DB-nullable on purpose, like {@code file.access_scope}/{@code link_role}: ddl-auto=update
     * can't add a NOT NULL column to a table that already has rows, so a pre-existing deployment's
     * rows would otherwise fail the migration outright. Application code always sets it on write. */
    @Enumerated(EnumType.STRING)
    private Role grantedRole;

    /** The per-invite capability token resolved by the public routes (see {@code PublicFileResolver}).
     * Kept alive after {@link #applyClaim} so an emailed link survives the invitee signing up.
     * Null only for a share created directly for an existing member. */
    private UUID token;

    /** Non-null only while a guest share is unclaimed — the invited email. Cleared by
     * {@link #applyClaim}; null for a direct member grant. */
    private String granteeEmail;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    FileShareJpaEntity(UUID fileId, UUID ownerId, UUID sharedWithUserId, Role grantedRole) {
        this(fileId, ownerId, sharedWithUserId, grantedRole, null, null);
    }

    FileShareJpaEntity(UUID fileId, UUID ownerId, UUID sharedWithUserId, Role grantedRole,
                       UUID token, String granteeEmail) {
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.sharedWithUserId = sharedWithUserId;
        this.grantedRole = grantedRole;
        this.token = token;
        this.granteeEmail = granteeEmail;
    }

    void applyGrantedRole(Role grantedRole) {
        this.grantedRole = grantedRole;
    }

    /** Mirrors {@link com.moduDrive.file.domain.model.FileShare#claim}: fills
     * {@code sharedWithUserId} and clears {@code granteeEmail}, but keeps {@code token} so the
     * emailed link keeps resolving. "Still unclaimed" is {@code sharedWithUserId == null} from
     * here on. */
    void applyClaim(UUID sharedWithUserId) {
        this.sharedWithUserId = sharedWithUserId;
        this.granteeEmail = null;
    }

    /** Mirrors {@link com.moduDrive.file.domain.model.FileShare#revokeToken}. */
    void applyRevokeToken() {
        this.token = null;
    }
}
