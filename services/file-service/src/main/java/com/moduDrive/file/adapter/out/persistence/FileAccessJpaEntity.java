package com.moduDrive.file.adapter.out.persistence;

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
@Table(name = "file_access", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_access_user_file", columnNames = {"user_id", "file_id"})
})
@Entity
class FileAccessJpaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID fileId;

    @Column(nullable = false)
    private LocalDateTime accessedAt;

    FileAccessJpaEntity(UUID userId, UUID fileId, LocalDateTime accessedAt) {
        this.userId = userId;
        this.fileId = fileId;
        this.accessedAt = accessedAt;
    }

    void touch(LocalDateTime accessedAt) {
        this.accessedAt = accessedAt;
    }
}
