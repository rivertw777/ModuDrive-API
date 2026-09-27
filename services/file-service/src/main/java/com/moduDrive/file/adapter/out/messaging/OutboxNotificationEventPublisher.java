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

    /** Keyed by {@code recipientId}, not fileId: the key says who the event is about, which is how a
     * person's notifications are found in {@code outbox_event}. */
    @Override
    public void publishFileShared(UUID fileId, UUID recipientId, String fileName, String role,
                                  boolean directory, String sharerName, String sharerEmail) {
        UUID eventId = UUID.randomUUID();
        outboxEventRecorder.record(FileQueues.FILE_SHARED, recipientId.toString(),
                new FileSharedNotified(eventId, fileId, recipientId, fileName, role, directory,
                        sharerName, sharerEmail));
    }
}
