package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.Permission;
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
@Table(name = "file_share")
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
    private Permission permission;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    FileShareJpaEntity(UUID fileId, UUID ownerId, UUID sharedWithUserId, Permission permission) {
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.sharedWithUserId = sharedWithUserId;
        this.permission = permission;
    }
}
