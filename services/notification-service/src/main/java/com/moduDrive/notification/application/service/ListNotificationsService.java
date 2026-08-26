package com.moduDrive.notification.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.notification.application.port.in.command.ListNotificationsCommand;
import com.moduDrive.notification.application.port.in.usecase.ListNotificationsUseCase;
import com.moduDrive.notification.application.port.out.FindNotificationPort;
import com.moduDrive.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class ListNotificationsService implements ListNotificationsUseCase {

    private final FindNotificationPort findNotificationPort;

    @Transactional(readOnly = true)
    @Override
    public Page<Notification> listNotifications(ListNotificationsCommand command) {
        return findNotificationPort.findByRecipientId(
                command.getRecipientId(), command.isUnreadOnly(), command.getPageable());
    }
}
