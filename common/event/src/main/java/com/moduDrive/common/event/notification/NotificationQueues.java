package com.moduDrive.common.event.notification;

/** Queue names shared by in-app notification event producers (file) and the consumer (notification-service).
 * Logical names, without a broker's own naming rules — the adapter maps them (SQS appends {@code .fifo}). */
public final class NotificationQueues {

    public static final String FILE_SHARED = "notification-file-shared";

    private NotificationQueues() {
    }
}
