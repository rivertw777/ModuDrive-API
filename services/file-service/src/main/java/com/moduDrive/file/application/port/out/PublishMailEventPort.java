package com.moduDrive.file.application.port.out;

import java.util.UUID;

public interface PublishMailEventPort {
    /** {@code linkToken} is non-null only for a guest invite (no ModuDrive member owns the email) —
     * it lets the invite mail carry a no-login link. */
    void publishShareInviteRequested(UUID fileId, String granteeEmail, String fileName, String role, UUID linkToken);
}
