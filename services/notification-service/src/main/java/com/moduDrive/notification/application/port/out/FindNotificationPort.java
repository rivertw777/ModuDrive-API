package com.moduDrive.notification.application.port.out;

import com.moduDrive.notification.domain.model.Notification;
import com.moduDrive.notification.domain.model.Notification.NotificationEventId;
import com.moduDrive.notification.domain.model.Notification.NotificationId;
import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface FindNotificationPort {

    boolean existsByEventId(NotificationEventId eventId);

    Optional<Notification> findById(NotificationId id);

    Page<Notification> findByRecipientId(NotificationRecipientId recipientId, boolean unreadOnly, Pageable pageable);
}
