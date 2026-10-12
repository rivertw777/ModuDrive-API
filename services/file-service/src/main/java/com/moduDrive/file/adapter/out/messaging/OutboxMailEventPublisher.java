package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.core.annotation.EventPublisher;
import com.moduDrive.common.event.file.FileQueues;
import com.moduDrive.common.event.file.ShareInviteMailRequested;
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
            UUID fileId, String granteeEmail, String fileName, boolean directory, String category,
            String granterName, String granterEmail, String message, UUID inviteToken) {
        outboxEventRecorder.record(FileQueues.SHARE_INVITE_MAIL_REQUESTED,
                new ShareInviteMailRequested(fileId, granteeEmail, fileName, directory, category,
                        granterName, granterEmail, message, inviteToken));
    }
}
