package com.moduDrive.common.event.notification;

/** Kafka topic names shared by in-app notification event producers (file) and the consumer (notification-service). */
public final class NotificationTopics {

    public static final String FILE_SHARED = "notification.file-shared";

    private NotificationTopics() {
    }
}
