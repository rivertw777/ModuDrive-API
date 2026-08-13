package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.Role;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    FileShareJpaEntity(UUID fileId, UUID ownerId, UUID sharedWithUserId, Role role) {
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.sharedWithUserId = sharedWithUserId;
        this.role = role;
    }

    void applyRole(Role role) {
        this.role = role;
    }
}
