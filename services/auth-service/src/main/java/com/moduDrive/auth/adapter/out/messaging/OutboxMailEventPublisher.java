package com.moduDrive.auth.adapter.out.messaging;

import com.moduDrive.auth.application.port.out.SendLoginVerificationMailPort;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.common.core.annotation.EventPublisher;
import com.moduDrive.common.event.auth.AuthQueues;
import com.moduDrive.common.event.auth.LoginVerificationMailRequested;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import lombok.RequiredArgsConstructor;

@EventPublisher
@RequiredArgsConstructor
class OutboxMailEventPublisher implements SendLoginVerificationMailPort {

    private final OutboxEventRecorder outboxEventRecorder;

    @Override
    public void sendLoginVerificationMail(MemberEmail memberEmail, String code) {
        outboxEventRecorder.record(AuthQueues.LOGIN_VERIFICATION_MAIL_REQUESTED, memberEmail.value(),
                new LoginVerificationMailRequested(memberEmail.value(), code));
    }
}
