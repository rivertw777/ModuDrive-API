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

    /** Auto-stamped from the caller's {@code X_USER_ID} header — see AuditingConfig#auditorAware.
     * Nullable: a background job/Kafka consumer write has no HTTP request to read the caller from. */
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

    /** No {@code @DeletedBy} auditing hook exists in Spring Data — a caller that soft-deletes a
     * row must set {@code deletedAt}/{@code deletedBy} itself. (The one real soft-delete flow,
     * {@code FileJpaEntity}'s trash/purge, does this via a dedicated JPQL {@code UPDATE} —
     * {@code SpringDataFileRepository.markPurged} — rather than through this entity, since it
     * also needs to skip the {@code @LastModifiedDate} bump a normal save would trigger.) */
    @Column
    private UUID deletedBy;

    @Column(nullable = false)
    private Boolean isDeleted = false;

}
