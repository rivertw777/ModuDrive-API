package com.moduDrive.file.application.port.out;

import java.util.UUID;

public interface PublishMailEventPort {
    /** {@code inviteToken} is non-null only for a guest invite (no ModuDrive member owns the email) —
     * it lets the invite mail carry a no-login link. {@code message} is the optional note the granter
     * typed into the share dialog; null/blank when they left it empty. */
    void publishShareInviteRequested(
            UUID fileId, String granteeEmail, String fileName, boolean directory, String category, String role,
            String granterName, String granterEmail, String message, UUID inviteToken);
}
