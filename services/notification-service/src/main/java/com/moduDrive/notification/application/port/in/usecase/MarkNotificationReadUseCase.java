package com.moduDrive.notification.application.port.in.usecase;

import com.moduDrive.notification.application.port.in.command.MarkNotificationReadCommand;
import com.moduDrive.notification.domain.model.Notification;

public interface MarkNotificationReadUseCase {
    Notification markNotificationRead(MarkNotificationReadCommand command);
}
