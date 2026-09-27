package com.moduDrive.auth.adapter.out.messaging;

import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.common.event.auth.AuthQueues;
import com.moduDrive.common.event.auth.LoginVerificationMailRequested;
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
    @DisplayName("새 기기 로그인 인증 메일을 보낼 때")
    class WhenSendingLoginVerificationMail {

        @Test
        void recordsToLoginVerificationQueueKeyedByEmail() {
            publisher.sendLoginVerificationMail(new MemberEmail("river@modudrive.com"), "042917");

            then(outboxEventRecorder).should().record(
                    AuthQueues.LOGIN_VERIFICATION_MAIL_REQUESTED, "river@modudrive.com",
                    new LoginVerificationMailRequested("river@modudrive.com", "042917"));
        }
    }
}
