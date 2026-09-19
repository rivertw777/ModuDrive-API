package com.moduDrive.notification.adapter.in.messaging;

import com.moduDrive.common.event.notification.FileSharedNotified;
import com.moduDrive.common.event.notification.NotificationQueues;
import com.moduDrive.notification.application.port.in.command.RecordFileSharedNotificationCommand;
import com.moduDrive.notification.application.port.in.usecase.RecordFileSharedNotificationUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NotificationEventListener {

    private final RecordFileSharedNotificationUseCase recordFileSharedNotificationUseCase;

    @SqsListener(NotificationQueues.FILE_SHARED)
    void onFileShared(FileSharedNotified event) {
        recordFileSharedNotificationUseCase.recordFileSharedNotification(
                new RecordFileSharedNotificationCommand(
                        event.eventId(), event.recipientId(), event.fileId(), event.fileName(), event.role(),
                        event.directory(), event.sharerName(), event.sharerEmail()));
    }
}
