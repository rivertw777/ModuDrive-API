package com.moduDrive.file.application.event;

import com.moduDrive.file.domain.model.Role;

import java.util.UUID;

/** {@code granteeId} and {@code linkToken} are mutually exclusive: a registered grantee gets an id
 * and a null token (they see the file after logging in); a guest grantee (no member owns the
 * email) gets a null id and their own pending-share token (see
 * {@link com.moduDrive.file.domain.model.FileShare#createPending}), so the invite mail can carry
 * a no-login link scoped to just that one invite — despite the field's name, it is never the
 * file's own {@code linkToken}. */
public record FileShareInvitedEvent(
        UUID fileId, UUID granterId, UUID granteeId, String granteeEmail, String fileName, Role role,
        UUID linkToken) {}
