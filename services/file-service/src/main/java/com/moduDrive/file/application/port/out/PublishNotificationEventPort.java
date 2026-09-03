package com.moduDrive.file.application.port.out;

import java.util.UUID;

public interface PublishNotificationEventPort {
    /** {@code recipientId} is always a registered member — a guest-by-email invite has no
     * ModuDrive account, so there is nothing to show an in-app notification to.
     * {@code sharerName}/{@code sharerEmail} identify the file owner, or null if unresolved.
     * {@code directory} is whether the shared item is a folder. */
    void publishFileShared(UUID fileId, UUID recipientId, String fileName, String role,
                           boolean directory, String sharerName, String sharerEmail);
}
