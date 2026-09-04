package com.moduDrive.file.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// One star per (user, file); the unique constraint makes a double-favorite a no-op at the DB.
@Table(name = "file_favorite", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_favorite_user_file", columnNames = {"user_id", "file_id"})
})
@Entity
@EntityListeners(AuditingEntityListener.class)
class FileFavoriteJpaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    /** When the star was created — set by JPA auditing, same pattern as {@code file_share}. Left
     * DB-nullable because ddl-auto=update can't add a NOT NULL column to a table that may already
     * hold rows; those read back null and the favorites-list query sorts them last. */
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    FileFavoriteJpaEntity(UUID userId, UUID fileId) {
        this.userId = userId;
        this.fileId = fileId;
    }
}
