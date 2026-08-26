package com.moduDrive.notification.application.port.in.command;

import com.moduDrive.notification.domain.model.Notification.NotificationId;
import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class MarkNotificationReadCommand {

    private final NotificationId notificationId;
    private final NotificationRecipientId recipientId;

    public MarkNotificationReadCommand(UUID notificationId, UUID recipientId) {
        this.notificationId = new NotificationId(notificationId);
        this.recipientId = new NotificationRecipientId(recipientId);
    }
}
