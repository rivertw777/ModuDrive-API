package com.moduDrive.notification.application.port.out;

import com.moduDrive.notification.application.port.in.usecase.NotificationPage;
import com.moduDrive.notification.domain.model.Notification;
import com.moduDrive.notification.domain.model.Notification.NotificationEventId;
import com.moduDrive.notification.domain.model.Notification.NotificationId;
import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;

import java.util.Optional;

public interface FindNotificationPort {

    boolean existsByEventId(NotificationEventId eventId);

    Optional<Notification> findById(NotificationId id);

    /** Newest first. */
    NotificationPage findByRecipientId(NotificationRecipientId recipientId, boolean unreadOnly, int page, int size);
}
