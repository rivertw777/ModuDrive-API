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

/** For an append-only/log-style table that only ever needs "who created this row and when" —
 * no update/soft-delete lifecycle, so no {@link BaseTimeEntity}. */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class CreatedAtEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Auto-stamped from the caller's {@code X_USER_ID} header — see AuditingConfig#auditorAware.
     * Nullable: a background job/Kafka consumer write has no HTTP request to read the caller from. */
    @CreatedBy
    @Column(updatable = false)
    private UUID createdBy;
}
