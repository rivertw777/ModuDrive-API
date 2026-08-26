package com.moduDrive.notification.application.port.in.command;

import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import lombok.Getter;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Getter
public class ListNotificationsCommand {

    private final NotificationRecipientId recipientId;
    private final boolean unreadOnly;
    private final Pageable pageable;

    public ListNotificationsCommand(UUID recipientId, boolean unreadOnly, Pageable pageable) {
        this.recipientId = new NotificationRecipientId(recipientId);
        this.unreadOnly = unreadOnly;
        this.pageable = pageable;
    }
}
