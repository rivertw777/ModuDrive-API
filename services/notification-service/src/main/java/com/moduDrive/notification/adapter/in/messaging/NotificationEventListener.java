package com.moduDrive.notification.adapter.in.messaging;

import com.moduDrive.common.event.notification.FileSharedNotified;
import com.moduDrive.common.event.notification.NotificationTopics;
import com.moduDrive.notification.application.port.in.command.RecordFileSharedNotificationCommand;
import com.moduDrive.notification.application.port.in.usecase.RecordFileSharedNotificationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NotificationEventListener {

    private final RecordFileSharedNotificationUseCase recordFileSharedNotificationUseCase;

    @KafkaListener(topics = NotificationTopics.FILE_SHARED)
    void onFileShared(FileSharedNotified event) {
        recordFileSharedNotificationUseCase.recordFileSharedNotification(
                new RecordFileSharedNotificationCommand(
                        event.eventId(), event.recipientId(), event.fileId(), event.fileName(), event.role()));
    }
}
