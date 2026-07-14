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
@Table(name = "file_version")
@Entity
@EntityListeners(AuditingEntityListener.class)
class FileVersionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID fileId;

    private Long fileSize;

    private int blockCount;

    @Column(nullable = false)
    private String s3Path;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    FileVersionJpaEntity(UUID fileId, Long fileSize, int blockCount, String s3Path) {
        this.fileId = fileId;
        this.fileSize = fileSize;
        this.blockCount = blockCount;
        this.s3Path = s3Path;
    }
}
