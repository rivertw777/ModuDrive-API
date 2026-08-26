package com.moduDrive.notification.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Notification {

    /** Null until the row is persisted — assigned by the database, like every other aggregate here. */
    private final UUID id;
    /** The producing Kafka event's id, and the idempotency key: Kafka is at-least-once, so the
     * same share can arrive more than once and must still produce exactly one notification. */
    private final UUID eventId;
    private final UUID recipientId;
    private final UUID fileId;
    private final String fileName;
    private final String role;
    /** Null while unread. */
    private final LocalDateTime readAt;
    /** Null until the row is persisted — filled in by JPA auditing. */
    private final LocalDateTime createdAt;

    public static Notification create(NotificationEventId eventId,
                                      NotificationRecipientId recipientId,
                                      NotificationFileId fileId,
                                      NotificationFileName fileName,
                                      NotificationRole role) {
        return new Notification(null, eventId.value(), recipientId.value(), fileId.value(),
                fileName.value(), role.value(), null, null);
    }

    public static Notification withId(NotificationId id,
                                      NotificationEventId eventId,
                                      NotificationRecipientId recipientId,
                                      NotificationFileId fileId,
                                      NotificationFileName fileName,
                                      NotificationRole role,
                                      LocalDateTime readAt,
                                      LocalDateTime createdAt) {
        return new Notification(id.value(), eventId.value(), recipientId.value(), fileId.value(),
                fileName.value(), role.value(), readAt, createdAt);
    }

    /** Returns a read copy; already-read notifications keep their original {@code readAt} so a
     * repeated mark-read call never rewrites when the user actually read it. */
    public Notification markRead(LocalDateTime readAt) {
        if (isRead()) {
            return this;
        }
        return new Notification(id, eventId, recipientId, fileId, fileName, role, readAt, createdAt);
    }

    public boolean isRead() {
        return readAt != null;
    }

    public record NotificationId(UUID value) {}
    public record NotificationEventId(UUID value) {}
    public record NotificationRecipientId(UUID value) {}
    public record NotificationFileId(UUID value) {}
    public record NotificationFileName(String value) {}
    public record NotificationRole(String value) {}
}
