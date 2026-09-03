package com.moduDrive.notification.fixture;

import com.moduDrive.notification.domain.model.Notification;
import com.moduDrive.notification.domain.model.Notification.NotificationDirectory;
import com.moduDrive.notification.domain.model.Notification.NotificationEventId;
import com.moduDrive.notification.domain.model.Notification.NotificationFileId;
import com.moduDrive.notification.domain.model.Notification.NotificationFileName;
import com.moduDrive.notification.domain.model.Notification.NotificationId;
import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import com.moduDrive.notification.domain.model.Notification.NotificationRole;
import com.moduDrive.notification.domain.model.Notification.NotificationSharerEmail;
import com.moduDrive.notification.domain.model.Notification.NotificationSharerName;

import java.time.LocalDateTime;
import java.util.UUID;

public final class NotificationTestFixture {

    public static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 25, 10, 0);
    public static final String SHARER_NAME = "홍길동";
    public static final String SHARER_EMAIL = "owner@modudrive.com";

    private NotificationTestFixture() {
    }

    public static Notification anUnreadNotification(UUID id, UUID recipientId) {
        return aNotification(id, recipientId, null);
    }

    public static Notification aReadNotification(UUID id, UUID recipientId, LocalDateTime readAt) {
        return aNotification(id, recipientId, readAt);
    }

    private static Notification aNotification(UUID id, UUID recipientId, LocalDateTime readAt) {
        return Notification.withId(
                new NotificationId(id),
                new NotificationEventId(UUID.randomUUID()),
                new NotificationRecipientId(recipientId),
                new NotificationFileId(UUID.randomUUID()),
                new NotificationFileName("report.pdf"),
                new NotificationRole("EDITOR"),
                new NotificationDirectory(false),
                new NotificationSharerName(SHARER_NAME),
                new NotificationSharerEmail(SHARER_EMAIL),
                readAt,
                CREATED_AT);
    }
}
