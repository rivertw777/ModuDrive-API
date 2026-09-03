package com.moduDrive.notification.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
// The unique constraint on event_id (not just the app-layer existsByEventId check) is what
// actually closes the TOCTOU window where two concurrent consumers of the same at-least-once
// Kafka redelivery both pass that check and both insert.
@Table(name = "notification", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_event_id", columnNames = "event_id")
})
@Entity
@EntityListeners(AuditingEntityListener.class)
class NotificationJpaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID recipientId;

    @Column(nullable = false)
    private UUID fileId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String role;

    /** Nullable only for rows recorded before this column existed — treated as a file (false) then. */
    private Boolean directory;

    /** Nullable — file-service may not have resolved them, and pre-existing rows predate the columns. */
    private String sharerName;
    private String sharerEmail;

    /** Null while unread. */
    private LocalDateTime readAt;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    NotificationJpaEntity(UUID eventId, UUID recipientId, UUID fileId, String fileName, String role,
                          boolean directory, String sharerName, String sharerEmail) {
        this.eventId = eventId;
        this.recipientId = recipientId;
        this.fileId = fileId;
        this.fileName = fileName;
        this.role = role;
        this.directory = directory;
        this.sharerName = sharerName;
        this.sharerEmail = sharerEmail;
    }

    void applyReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }
}
