package com.moduDrive.common.event.notification;

import java.util.UUID;

/** Published by file-service (topic {@link NotificationTopics#FILE_SHARED}) after a share to a
 * registered member commits — a guest-by-email invite has no ModuDrive account to notify in-app.
 * {@code eventId} is minted by the producer and is the consumer's idempotency key: Kafka is
 * at-least-once, so notification-service dedupes redeliveries on it.
 * {@code sharerName}/{@code sharerEmail} identify the member who shared the file; both null when
 * file-service could not resolve them — a best-effort enrichment that must never block the share
 * itself. {@code directory} tells the feed whether the shared item is a folder or a file. */
public record FileSharedNotified(UUID eventId, UUID fileId, UUID recipientId, String fileName, String role,
                                 boolean directory, String sharerName, String sharerEmail) {
}
