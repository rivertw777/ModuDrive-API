package com.moduDrive.common.event.notification;

/** SQS FIFO queue names shared by in-app notification event producers (file) and the consumer (notification-service). */
public final class NotificationQueues {

    public static final String FILE_SHARED = "notification-file-shared.fifo";

    private NotificationQueues() {
    }
}
