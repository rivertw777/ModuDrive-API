package com.moduDrive.notification.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.notification.application.port.in.command.ListNotificationsCommand;
import com.moduDrive.notification.application.port.in.usecase.ListNotificationsUseCase;
import com.moduDrive.notification.application.port.in.usecase.NotificationPage;
import com.moduDrive.notification.application.port.out.FindNotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class ListNotificationsService implements ListNotificationsUseCase {

    private final FindNotificationPort findNotificationPort;

    @Transactional(readOnly = true)
    @Override
    public NotificationPage listNotifications(ListNotificationsCommand command) {
        return findNotificationPort.findByRecipientId(
                command.getRecipientId(), command.isUnreadOnly(),
                command.getPage(), command.getSize());
    }
}
