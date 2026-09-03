package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.event.notification.FileSharedNotified;
import com.moduDrive.common.event.notification.NotificationTopics;
import com.moduDrive.file.application.port.out.PublishNotificationEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class KafkaNotificationEventPublisher implements PublishNotificationEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Keyed by {@code recipientId}, not fileId: a file shared with many people should fan out
     * across partitions per person, and per-recipient ordering is the only ordering that matters
     * for a notification feed. */
    @Override
    public void publishFileShared(UUID fileId, UUID recipientId, String fileName, String role,
                                  boolean directory, String sharerName, String sharerEmail) {
        UUID eventId = UUID.randomUUID();
        kafkaTemplate.send(NotificationTopics.FILE_SHARED, recipientId.toString(),
                        new FileSharedNotified(eventId, fileId, recipientId, fileName, role, directory,
                                sharerName, sharerEmail))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish file shared notification event: fileId={}, recipientId={}",
                                fileId, recipientId, ex);
                    }
                });
    }
}
