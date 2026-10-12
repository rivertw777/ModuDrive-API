package com.moduDrive.common.infrastructure.jpa.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseTimeEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** From the {@code X_USER_ID} header (AuditingConfig#auditorAware); null outside an HTTP request. */
    @CreatedBy
    @Column(updatable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column
    private UUID updatedBy;

    @Column
    private LocalDateTime deletedAt;

    /** Spring Data has no {@code @DeletedBy}: whoever soft-deletes sets {@code deletedAt}/{@code deletedBy}
     * itself (file-service does it in {@code SpringDataFileRepository.markPurged}). */
    @Column
    private UUID deletedBy;

    @Column(nullable = false)
    private Boolean isDeleted = false;

}
