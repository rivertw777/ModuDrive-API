package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.event.mail.MailTopics;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.member.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class KafkaMailEventPublisher implements PublishMailEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishVerificationRequested(String email, String verificationToken) {
        kafkaTemplate.send(MailTopics.VERIFICATION_REQUESTED, email,
                        new VerificationMailRequested(email, verificationToken))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish verification mail event: email={}", email, ex);
                    }
                });
    }
}
