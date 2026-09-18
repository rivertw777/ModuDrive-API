package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.event.mail.MailTopics;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.member.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class KafkaMailEventPublisher implements PublishMailEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishVerificationRequested(String email, String verificationCode) {
        kafkaTemplate.send(MailTopics.VERIFICATION_REQUESTED, email,
                new VerificationMailRequested(email, verificationCode));
    }
}
