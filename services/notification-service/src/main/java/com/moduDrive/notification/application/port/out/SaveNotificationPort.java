package com.moduDrive.notification.application.port.out;

import com.moduDrive.notification.domain.model.Notification;

public interface SaveNotificationPort {

    /** Inserts a brand-new notification in its own transaction (see the adapter) so that a
     * concurrent duplicate {@code event_id} rolls back only this insert, never the caller's
     * transaction. */
    Notification insertNotification(Notification notification);

    /** Persists a mutation to an already-persisted notification (e.g. marking it read). */
    Notification saveNotification(Notification notification);
}
