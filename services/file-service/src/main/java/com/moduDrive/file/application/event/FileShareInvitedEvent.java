package com.moduDrive.file.application.event;

import com.moduDrive.file.domain.model.FileCategory;
import com.moduDrive.file.domain.model.Role;

import java.util.UUID;

/** {@code granteeId} and {@code inviteToken} are mutually exclusive: a registered grantee gets an id
 * and a null token (they see the file after logging in); a guest grantee (no member owns the
 * email) gets a null id and their own pending-share token (see
 * {@link com.moduDrive.file.domain.model.FileShare#createPending}), so the invite mail can carry
 * a no-login link scoped to just that one invite. {@code message} is the optional note the granter
 * typed into the share dialog (Drive-style); null/blank when they left it empty. {@code category}
 * lets the invite mail pick a matching file-type icon without reimplementing the extension-to-
 * category mapping — file-service (via {@link FileCategory#of}) is the one source of truth for it. */
public record FileShareInvitedEvent(
        UUID fileId, UUID granterId, String granterName, String granterEmail, UUID granteeId, String granteeEmail,
        String fileName, boolean directory, FileCategory category, Role role, String message, UUID inviteToken) {}
