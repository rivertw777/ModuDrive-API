package com.moduDrive.notification.application.port.out;

import com.moduDrive.notification.domain.model.Notification;

public interface SaveNotificationPort {

    /** Runs in its own transaction, so a duplicate {@code event_id} rolls back only this insert. */
    Notification insertNotification(Notification notification);

    Notification saveNotification(Notification notification);
}
