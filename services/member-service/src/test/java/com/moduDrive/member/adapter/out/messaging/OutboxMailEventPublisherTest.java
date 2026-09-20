package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.event.mail.MailQueues;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OutboxMailEventPublisherTest {

    @Mock
    private OutboxEventRecorder outboxEventRecorder;
    @InjectMocks
    private OutboxMailEventPublisher publisher;

    @Nested
    @DisplayName("회원가입 인증 메일 이벤트를 발행할 때")
    class WhenPublishingVerificationRequested {

        @Test
        void sendsPayloadToVerificationTopicKeyedByEmail() {
            publisher.publishVerificationRequested("river@modudrive.com", "042917");

            then(outboxEventRecorder).should().record(
                    MailQueues.VERIFICATION_REQUESTED, "river@modudrive.com",
                    new VerificationMailRequested("river@modudrive.com", "042917"));
        }
    }
}
