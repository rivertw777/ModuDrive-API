package com.moduDrive.common.infrastructure.jpa.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** For append-only tables that need only creation auditing, not {@link BaseTimeEntity}'s update/soft-delete columns. */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class CreatedAtEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** From the {@code X_USER_ID} header (AuditingConfig#auditorAware); null outside an HTTP request. */
    @CreatedBy
    @Column(updatable = false)
    private UUID createdBy;
}
