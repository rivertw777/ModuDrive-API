package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.event.notification.FileSharedNotified;
import com.moduDrive.common.event.notification.NotificationTopics;
import com.moduDrive.common.infrastructure.outbox.OutboxEventRecorder;
import com.moduDrive.file.application.port.out.PublishNotificationEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class KafkaNotificationEventPublisher implements PublishNotificationEventPort {

    private final OutboxEventRecorder outboxEventRecorder;

    /** Keyed by {@code recipientId}, not fileId: a file shared with many people should fan out
     * across partitions per person, and per-recipient ordering is the only ordering that matters
     * for a notification feed. */
    @Override
    public void publishFileShared(UUID fileId, UUID recipientId, String fileName, String role,
                                  boolean directory, String sharerName, String sharerEmail) {
        UUID eventId = UUID.randomUUID();
        outboxEventRecorder.record(NotificationTopics.FILE_SHARED, recipientId.toString(),
                new FileSharedNotified(eventId, fileId, recipientId, fileName, role, directory,
                        sharerName, sharerEmail));
    }
}
