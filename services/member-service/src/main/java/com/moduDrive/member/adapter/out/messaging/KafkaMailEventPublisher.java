package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.event.mail.MailTopics;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.common.infrastructure.outbox.OutboxEventRecorder;
import com.moduDrive.member.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class KafkaMailEventPublisher implements PublishMailEventPort {

    private final OutboxEventRecorder outboxEventRecorder;

    @Override
    public void publishVerificationRequested(String email, String verificationCode) {
        outboxEventRecorder.record(MailTopics.VERIFICATION_REQUESTED, email,
                new VerificationMailRequested(email, verificationCode));
    }
}
