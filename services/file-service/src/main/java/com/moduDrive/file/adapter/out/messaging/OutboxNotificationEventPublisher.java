package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.core.annotation.EventPublisher;
import com.moduDrive.common.event.file.FileQueues;
import com.moduDrive.common.event.file.FileSharedNotified;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import com.moduDrive.file.application.port.out.PublishNotificationEventPort;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@EventPublisher
@RequiredArgsConstructor
class OutboxNotificationEventPublisher implements PublishNotificationEventPort {

    private final OutboxEventRecorder outboxEventRecorder;

    @Override
    public void publishFileShared(UUID fileId, UUID recipientId, String fileName, String role,
                                  boolean directory, String sharerName, String sharerEmail) {
        UUID eventId = UUID.randomUUID();
        outboxEventRecorder.record(FileQueues.FILE_SHARED,
                new FileSharedNotified(eventId, fileId, recipientId, fileName, role, directory,
                        sharerName, sharerEmail));
    }
}
