package com.moduDrive.notification.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.notification.application.port.in.command.MarkNotificationReadCommand;
import com.moduDrive.notification.application.port.in.usecase.MarkNotificationReadUseCase;
import com.moduDrive.notification.application.port.out.FindNotificationPort;
import com.moduDrive.notification.application.port.out.SaveNotificationPort;
import com.moduDrive.notification.domain.model.Notification;
import com.moduDrive.notification.exception.NotificationExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
@RequiredArgsConstructor
class MarkNotificationReadService implements MarkNotificationReadUseCase {

    private final FindNotificationPort findNotificationPort;
    private final SaveNotificationPort saveNotificationPort;

    /** Someone else's notification answers 404, not 403: a notification id is guessable enough
     * that "exists but not yours" would let a stranger probe who got notified about what. */
    @Transactional
    @Override
    public Notification markNotificationRead(MarkNotificationReadCommand command) {
        Notification notification = findNotificationPort.findById(command.getNotificationId())
                .filter(found -> found.getRecipientId().equals(command.getRecipientId().value()))
                .orElseThrow(() -> new BusinessException(NotificationExceptionCase.NOTIFICATION_NOT_FOUND));

        if (notification.isRead()) {
            return notification;
        }
        return saveNotificationPort.saveNotification(notification.markRead(LocalDateTime.now()));
    }
}
