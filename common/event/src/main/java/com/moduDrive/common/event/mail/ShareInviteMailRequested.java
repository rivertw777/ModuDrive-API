package com.moduDrive.common.event.mail;

import java.util.UUID;

/** Published by file-service (topic {@link MailTopics#SHARE_INVITE_REQUESTED}) after a share invite commits.
 * {@code inviteToken} is only set when the invite went to an email with no ModuDrive member (a "guest"
 * invite) — it is that pending share's own capability token, and mail-service turns it into a no-login
 * link; a registered member gets null and signs in instead. {@code message} is the optional note the
 * granter typed into the share dialog (Drive-style); null/blank when they left it empty. {@code category}
 * is file-service's {@code FileCategory} name (IMAGE/VIDEO/AUDIO/DOCUMENT/OTHER) so the invite mail can
 * pick a matching file-type icon without reimplementing the extension-to-category mapping itself. */
public record ShareInviteMailRequested(
        UUID fileId, String granteeEmail, String fileName, boolean directory, String category, String role,
        String granterName, String granterEmail, String message, UUID inviteToken) {
}
