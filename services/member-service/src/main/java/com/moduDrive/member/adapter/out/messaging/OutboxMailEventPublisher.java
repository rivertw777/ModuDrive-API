package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.core.annotation.EventPublisher;
import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.event.member.SignUpVerificationMailRequested;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import com.moduDrive.member.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;

@EventPublisher
@RequiredArgsConstructor
class OutboxMailEventPublisher implements PublishMailEventPort {

    private final OutboxEventRecorder outboxEventRecorder;

    @Override
    public void publishVerificationRequested(String email, String verificationCode) {
        outboxEventRecorder.record(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, email,
                new SignUpVerificationMailRequested(email, verificationCode));
    }
}
