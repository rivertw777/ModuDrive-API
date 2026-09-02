package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.BaseTimeEntity;
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
@Table(name = "namespace")
@Entity
class NamespaceJpaEntity extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private String rootPath;

    @Column(nullable = false)
    private long quotaBytes;

    NamespaceJpaEntity(UUID userId, String rootPath, long quotaBytes) {
        this.userId = userId;
        this.rootPath = rootPath;
        this.quotaBytes = quotaBytes;
    }
}
