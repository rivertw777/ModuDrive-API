package com.moduDrive.notification.application.service;

import com.moduDrive.notification.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.notification.application.port.out.SendMailPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SendVerificationMailServiceTest {

    @Mock
    private SendMailPort sendMailPort;

    private SendVerificationMailService service() {
        return new SendVerificationMailService(sendMailPort, "http://localhost:10001");
    }

    @Nested
    @DisplayName("인증 메일 발송을 요청받았을 때")
    class WhenRequested {

        @Test
        void sendsMailWithVerificationLinkBuiltFromBaseUrl() {
            SendVerificationMailCommand command =
                    new SendVerificationMailCommand("river@modudrive.com", "some-token");

            service().sendVerificationMail(command);

            then(sendMailPort).should().send(
                    eq("river@modudrive.com"),
                    contains("인증"),
                    contains("http://localhost:10001/api/v1/member/verify-email?token=some-token"));
        }
    }
}
