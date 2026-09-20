package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.core.annotation.EventPublisher;
import com.moduDrive.common.event.mail.MailDestinations;
import com.moduDrive.common.event.mail.ShareInviteMailRequested;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import com.moduDrive.file.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@EventPublisher
@RequiredArgsConstructor
class OutboxMailEventPublisher implements PublishMailEventPort {

    private final OutboxEventRecorder outboxEventRecorder;

    @Override
    public void publishShareInviteRequested(
            UUID fileId, String granteeEmail, String fileName, boolean directory, String category, String role,
            String granterName, String granterEmail, String message, UUID inviteToken) {
        // Keyed by the recipient, not the file: mails to different people have no order between them,
        // and one bad address retrying must not hold up everyone else invited to the same file.
        outboxEventRecorder.record(MailDestinations.SHARE_INVITE_REQUESTED, granteeEmail,
                new ShareInviteMailRequested(fileId, granteeEmail, fileName, directory, category, role,
                        granterName, granterEmail, message, inviteToken));
    }
}
