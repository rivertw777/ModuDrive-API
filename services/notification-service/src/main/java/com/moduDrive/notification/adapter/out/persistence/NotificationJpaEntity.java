package com.moduDrive.notification.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
// The unique constraint on event_id (not just the app-layer existsByEventId check) is what
// actually closes the TOCTOU window where two concurrent consumers of the same at-least-once
// SQS redelivery both pass that check and both insert.
@Table(name = "notification", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_event_id", columnNames = "event_id")
})
@Entity
class NotificationJpaEntity extends CreatedAtEntity {

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

    @Column(nullable = false)
    private boolean directory;

    /** Nullable — file-service may not have resolved them. */
    private String sharerName;
    private String sharerEmail;

    /** Null while unread. */
    private LocalDateTime readAt;

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
