package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.CreatedAtEntity;
import com.moduDrive.file.domain.model.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
// The unique constraints, not the existsBy checks, stop two concurrent invites for the same
// grantee. NULLs never collide, so each constraint only polices its own kind of row (member or
// guest).
@Table(name = "file_share", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_share_file_user", columnNames = {"file_id", "shared_with_user_id"}),
        @UniqueConstraint(name = "uk_file_share_file_email", columnNames = {"file_id", "granteeEmail"}),
        @UniqueConstraint(name = "uk_file_share_token", columnNames = {"token"})
})
@Entity
class FileShareJpaEntity extends CreatedAtEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private UUID fileId;

    @Column(nullable = false)
    private UUID ownerId;

    /** Null for a pending guest share — see {@link com.moduDrive.file.domain.model.FileShare}. */
    private UUID sharedWithUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role grantedRole;

    /** A guest share's invite token (see {@code PublicFileResolver}), kept after
     * {@link #applyClaim}. Null for a share made directly for a member. */
    private UUID token;

    /** Non-null only while a guest share is unclaimed — the invited email. Cleared by
     * {@link #applyClaim}; null for a direct member grant. */
    private String granteeEmail;

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

    /** Mirrors {@link com.moduDrive.file.domain.model.FileShare#claim}. */
    void applyClaim(UUID sharedWithUserId) {
        this.sharedWithUserId = sharedWithUserId;
        this.granteeEmail = null;
    }
}
