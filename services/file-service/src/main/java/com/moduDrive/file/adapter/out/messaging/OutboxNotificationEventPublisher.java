package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.core.annotation.EventPublisher;
import com.moduDrive.common.event.notification.FileSharedNotified;
import com.moduDrive.common.event.notification.NotificationDestinations;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import com.moduDrive.file.application.port.out.PublishNotificationEventPort;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@EventPublisher
@RequiredArgsConstructor
class OutboxNotificationEventPublisher implements PublishNotificationEventPort {

    private final OutboxEventRecorder outboxEventRecorder;

    /** Keyed by {@code recipientId}, not fileId: the key is the FIFO message group, so a file shared
     * with many people fans out into one group per person, and per-recipient ordering is the only
     * ordering that matters for a notification feed. */
    @Override
    public void publishFileShared(UUID fileId, UUID recipientId, String fileName, String role,
                                  boolean directory, String sharerName, String sharerEmail) {
        UUID eventId = UUID.randomUUID();
        outboxEventRecorder.record(NotificationDestinations.FILE_SHARED, recipientId.toString(),
                new FileSharedNotified(eventId, fileId, recipientId, fileName, role, directory,
                        sharerName, sharerEmail));
    }
}
