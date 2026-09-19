package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.event.mail.MailQueues;
import com.moduDrive.common.event.mail.ShareInviteMailRequested;
import com.moduDrive.common.infrastructure.outbox.OutboxEventRecorder;
import com.moduDrive.file.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class OutboxMailEventPublisher implements PublishMailEventPort {

    private final OutboxEventRecorder outboxEventRecorder;

    @Override
    public void publishShareInviteRequested(
            UUID fileId, String granteeEmail, String fileName, boolean directory, String category, String role,
            String granterName, String granterEmail, String message, UUID inviteToken) {
        outboxEventRecorder.record(MailQueues.SHARE_INVITE_REQUESTED, fileId.toString(),
                new ShareInviteMailRequested(fileId, granteeEmail, fileName, directory, category, role,
                        granterName, granterEmail, message, inviteToken));
    }
}
