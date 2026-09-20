package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.core.annotation.EventPublisher;
import com.moduDrive.common.event.mail.MailDestinations;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import com.moduDrive.member.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;

@EventPublisher
@RequiredArgsConstructor
class OutboxMailEventPublisher implements PublishMailEventPort {

    private final OutboxEventRecorder outboxEventRecorder;

    @Override
    public void publishVerificationRequested(String email, String verificationCode) {
        outboxEventRecorder.record(MailDestinations.VERIFICATION_REQUESTED, email,
                new VerificationMailRequested(email, verificationCode));
    }
}
