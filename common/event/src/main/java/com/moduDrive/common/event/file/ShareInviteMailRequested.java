package com.moduDrive.common.event.file;

import java.util.UUID;

/** Published by file-service (queue {@link FileQueues#SHARE_INVITE_MAIL_REQUESTED}) after a share invite commits.
 * {@code inviteToken} is set only for a guest invite (no member with that email): it is the pending
 * share's capability token, which mail-service turns into a no-login link. {@code message} is the
 * granter's optional note, null or blank if empty. {@code category} is a file-service {@code FileCategory}
 * name, used to pick the mail's file-type icon. */
public record ShareInviteMailRequested(
        UUID fileId, String granteeEmail, String fileName, boolean directory, String category,
        String granterName, String granterEmail, String message, UUID inviteToken) {
}
