package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.event.mail.MailTopics;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class KafkaMailEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks
    private KafkaMailEventPublisher kafkaMailEventPublisher;

    @Nested
    @DisplayName("회원가입 인증 메일 이벤트를 발행할 때")
    class WhenPublishingVerificationRequested {

        @Test
        void sendsPayloadToVerificationTopicKeyedByEmail() {
            given(kafkaTemplate.send(any(String.class), any(String.class), any()))
                    .willReturn(CompletableFuture.completedFuture(null));

            kafkaMailEventPublisher.publishVerificationRequested("river@modudrive.com", "042917");

            then(kafkaTemplate).should().send(
                    MailTopics.VERIFICATION_REQUESTED, "river@modudrive.com",
                    new VerificationMailRequested("river@modudrive.com", "042917"));
        }
    }
}
