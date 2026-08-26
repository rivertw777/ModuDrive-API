package com.moduDrive.common.event.notification;

import java.util.UUID;

/** Published by file-service (topic {@link NotificationTopics#FILE_SHARED}) after a share to a
 * registered member commits — a guest-by-email invite has no ModuDrive account to notify in-app.
 * {@code eventId} is minted by the producer and is the consumer's idempotency key: Kafka is
 * at-least-once, so notification-service dedupes redeliveries on it. */
public record FileSharedNotified(UUID eventId, UUID fileId, UUID recipientId, String fileName, String role) {
}
