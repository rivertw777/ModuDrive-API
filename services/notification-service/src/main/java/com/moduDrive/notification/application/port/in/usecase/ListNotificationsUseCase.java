package com.moduDrive.notification.application.port.in.usecase;

import com.moduDrive.notification.application.port.in.command.ListNotificationsCommand;

public interface ListNotificationsUseCase {
    NotificationPage listNotifications(ListNotificationsCommand command);
}
