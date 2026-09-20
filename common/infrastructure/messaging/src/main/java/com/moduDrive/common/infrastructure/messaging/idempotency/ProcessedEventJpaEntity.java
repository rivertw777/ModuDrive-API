package com.moduDrive.common.infrastructure.messaging.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One handled message. The unique constraint is the real guard: it rejects a second row for the
 * same event even if two consumers check at the same moment. */
@Getter
@Entity
@Table(name = "processed_event", uniqueConstraints =
        @UniqueConstraint(name = "uk_processed_event", columnNames = {"queue_name", "deduplication_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProcessedEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "queue_name", nullable = false)
    private String queueName;

    @Column(name = "deduplication_id", nullable = false)
    private String deduplicationId;

    @Column(nullable = false)
    private Instant processedAt;

    ProcessedEventJpaEntity(String queueName, String deduplicationId) {
        this.queueName = queueName;
        this.deduplicationId = deduplicationId;
        this.processedAt = Instant.now();
    }
}
