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

    /** SQS delivers at-least-once. {@code existsByEventId} skips a plain redelivery; a concurrent duplicate
     * hits the {@code event_id} unique constraint in {@code insertNotification}'s own transaction and is
     * swallowed here instead of going to the DLQ. */
    @Transactional
    @Override
    public void recordFileSharedNotification(RecordFileSharedNotificationCommand command) {
        if (findNotificationPort.existsByEventId(command.getEventId())) {
            log.debug("Skipping already-recorded notification: eventId={}", command.getEventId().value());
            return;
        }

        Notification notification = Notification.create(
                command.getEventId(), command.getRecipientId(), command.getFileId(),
                command.getFileName(), command.getRole(), command.getDirectory(),
                command.getSharerName(), command.getSharerEmail());
        try {
            saveNotificationPort.insertNotification(notification);
        } catch (DuplicateKeyException e) {
            // Only the unique-key violation: other constraint failures mean a malformed event and must propagate.
            log.debug("Notification already recorded concurrently: eventId={}", command.getEventId().value());
        }
    }
}
