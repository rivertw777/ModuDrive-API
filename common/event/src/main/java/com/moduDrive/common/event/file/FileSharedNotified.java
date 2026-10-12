package com.moduDrive.common.event.file;

import java.util.UUID;

/** Published by file-service (queue {@link FileQueues#FILE_SHARED}) after a share to a registered
 * member commits; guest invites have no account to notify. notification-service stores {@code eventId}
 * as the unique {@code event_id}, a second guard behind the {@code DeduplicationId} check.
 * {@code sharerName}/{@code sharerEmail} are best effort: both null when file-service could not
 * resolve them, since that must never block the share. */
public record FileSharedNotified(UUID eventId, UUID fileId, UUID recipientId, String fileName, String role,
                                 boolean directory, String sharerName, String sharerEmail) {
}
