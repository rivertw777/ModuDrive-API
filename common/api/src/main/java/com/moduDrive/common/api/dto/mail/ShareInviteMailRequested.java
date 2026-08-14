package com.moduDrive.common.api.dto.mail;

import java.util.UUID;

/** Published by file-service (topic {@link MailTopics#SHARE_INVITE_REQUESTED}) after a share invite commits. */
public record ShareInviteMailRequested(UUID fileId, String granteeEmail, String fileName, String role) {
}
