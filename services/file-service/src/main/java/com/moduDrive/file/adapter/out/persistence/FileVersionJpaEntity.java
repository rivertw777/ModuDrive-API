package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "file_version")
@Entity
class FileVersionJpaEntity extends CreatedAtEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private UUID fileId;

    @Column(nullable = false)
    private UUID ownerId;

    private Long fileSize;

    @Column(nullable = false)
    private UUID uploadId;

    // Eager: every reader (download, zip, purge) needs the blocklist, and versions are mapped to
    // the domain outside a transaction. BatchSize keeps a zip of many files to a few queries.
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 100)
    @CollectionTable(name = "file_version_block", joinColumns = @JoinColumn(name = "version_id"))
    @OrderColumn(name = "idx")
    @Column(name = "hash", nullable = false, length = 64)
    private List<String> hashes = new ArrayList<>();

    FileVersionJpaEntity(UUID fileId, UUID ownerId, Long fileSize, UUID uploadId, List<String> hashes) {
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.fileSize = fileSize;
        this.uploadId = uploadId;
        this.hashes = new ArrayList<>(hashes);
    }
}
