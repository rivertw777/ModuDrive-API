package com.moduDrive.notification.adapter.in.messaging;

import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.notification.FileSharedNotified;
import com.moduDrive.common.event.notification.NotificationDestinations;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.notification.application.port.in.command.RecordFileSharedNotificationCommand;
import com.moduDrive.notification.application.port.in.usecase.RecordFileSharedNotificationUseCase;
import com.moduDrive.common.infrastructure.sqs.SqsQueues;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.annotation.Transactional;

@EventListener
@RequiredArgsConstructor
class NotificationEventListener {

    private final RecordFileSharedNotificationUseCase recordFileSharedNotificationUseCase;
    private final ProcessedEvents processedEvents;

    /** @Transactional so the "already handled" record commits with the notification row: if recording
     * fails, both are rolled back and the retry starts over. */
    @Transactional
    @SqsListener(NotificationDestinations.FILE_SHARED + SqsQueues.FIFO_SUFFIX)
    void onFileShared(FileSharedNotified event,
                      @Header(MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER) String deduplicationId) {
        if (processedEvents.isProcessed(NotificationDestinations.FILE_SHARED, deduplicationId)) {
            return;
        }
        recordFileSharedNotificationUseCase.recordFileSharedNotification(
                new RecordFileSharedNotificationCommand(
                        event.eventId(), event.recipientId(), event.fileId(), event.fileName(), event.role(),
                        event.directory(), event.sharerName(), event.sharerEmail()));
        processedEvents.markProcessed(NotificationDestinations.FILE_SHARED, deduplicationId);
    }
}
