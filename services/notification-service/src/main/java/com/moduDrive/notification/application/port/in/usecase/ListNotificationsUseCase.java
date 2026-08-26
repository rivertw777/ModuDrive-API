package com.moduDrive.notification.application.port.in.usecase;

import com.moduDrive.notification.application.port.in.command.ListNotificationsCommand;
import com.moduDrive.notification.domain.model.Notification;
import org.springframework.data.domain.Page;

public interface ListNotificationsUseCase {
    Page<Notification> listNotifications(ListNotificationsCommand command);
}
