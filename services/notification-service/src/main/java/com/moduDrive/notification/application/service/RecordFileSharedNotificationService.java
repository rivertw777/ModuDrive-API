package com.moduDrive.notification.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.notification.application.port.in.command.RecordFileSharedNotificationCommand;
import com.moduDrive.notification.application.port.in.usecase.RecordFileSharedNotificationUseCase;
import com.moduDrive.notification.application.port.out.FindNotificationPort;
import com.moduDrive.notification.application.port.out.SaveNotificationPort;
import com.moduDrive.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@UseCase
@RequiredArgsConstructor
class RecordFileSharedNotificationService implements RecordFileSharedNotificationUseCase {

    private final FindNotificationPort findNotificationPort;
    private final SaveNotificationPort saveNotificationPort;

    /**
     * Kafka delivers at-least-once, so the same share event can arrive more than once. The
     * {@code existsByEventId} check catches the ordinary redelivery; the {@code event_id} unique
     * constraint closes the TOCTOU window where two concurrent consumers both pass that check.
     * {@code insertNotification} runs in its own REQUIRES_NEW transaction precisely so that
     * constraint violation can be caught here, in a transaction it never touched, and swallowed
     * as "already recorded" rather than rethrown — which would otherwise send a duplicate
     * straight to the dead-letter topic.
     */
    @Transactional
    @Override
    public void recordFileSharedNotification(RecordFileSharedNotificationCommand command) {
        if (findNotificationPort.existsByEventId(command.getEventId())) {
            log.debug("Skipping already-recorded notification: eventId={}", command.getEventId().value());
            return;
        }

        Notification notification = Notification.create(
                command.getEventId(), command.getRecipientId(), command.getFileId(),
                command.getFileName(), command.getRole());
        try {
            saveNotificationPort.insertNotification(notification);
        } catch (DuplicateKeyException e) {
            // Narrowed to the unique-key subtype on purpose: a NOT NULL or length violation on a
            // malformed event is a real failure and must keep propagating to the dead-letter
            // topic, not get swallowed under the same "already recorded" assumption.
            log.debug("Notification already recorded concurrently: eventId={}", command.getEventId().value());
        }
    }
}
