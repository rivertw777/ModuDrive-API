package com.moduDrive.common.event.notification;

/** Queue names shared by in-app notification event producers (file) and the consumer (notification-service).
 * The queue name as the broker knows it. */
public final class NotificationQueues {

    public static final String FILE_SHARED = "notification-file-shared";

    private NotificationQueues() {
    }
}
