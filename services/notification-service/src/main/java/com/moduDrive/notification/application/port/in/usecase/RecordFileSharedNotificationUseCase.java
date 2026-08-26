package com.moduDrive.notification.application.port.in.usecase;

import com.moduDrive.notification.application.port.in.command.RecordFileSharedNotificationCommand;

public interface RecordFileSharedNotificationUseCase {
    void recordFileSharedNotification(RecordFileSharedNotificationCommand command);
}
