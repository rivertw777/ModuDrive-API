package com.moduDrive.common.event.notification;

import java.util.UUID;

/** Published by file-service (queue {@link NotificationQueues#FILE_SHARED}) after a share to a
 * registered member commits — a guest-by-email invite has no ModuDrive account to notify in-app.
 * {@code eventId} is minted by the producer and identifies the share event. Redeliveries are dropped
 * first by the listener's check on the {@code DeduplicationId} message attribute; notification-service
 * also stores {@code eventId} as the notification's unique {@code event_id} and skips an event it has
 * already recorded — a second guard at the row itself.
 * {@code sharerName}/{@code sharerEmail} identify the member who shared the file; both null when
 * file-service could not resolve them — a best-effort enrichment that must never block the share
 * itself. {@code directory} tells the feed whether the shared item is a folder or a file. */
public record FileSharedNotified(UUID eventId, UUID fileId, UUID recipientId, String fileName, String role,
                                 boolean directory, String sharerName, String sharerEmail) {
}
