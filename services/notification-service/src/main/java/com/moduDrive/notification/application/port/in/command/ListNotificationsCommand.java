package com.moduDrive.notification.application.port.in.command;

import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ListNotificationsCommand {

    public static final int MAX_PAGE_SIZE = 100;

    private final NotificationRecipientId recipientId;
    private final boolean unreadOnly;
    private final int page;
    private final int size;

    public ListNotificationsCommand(UUID recipientId, boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        this.recipientId = new NotificationRecipientId(recipientId);
        this.unreadOnly = unreadOnly;
        this.page = page;
        this.size = size;
    }
}
