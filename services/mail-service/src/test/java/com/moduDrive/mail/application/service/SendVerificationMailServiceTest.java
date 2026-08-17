package com.moduDrive.mail.application.service;

import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.out.SendMailPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SendVerificationMailServiceTest {

    @Mock
    private SendMailPort sendMailPort;
    @InjectMocks
    private SendVerificationMailService sendVerificationMailService;

    @Nested
    @DisplayName("인증 메일 발송을 요청받았을 때")
    class WhenRequested {

        @Test
        void sendsMailContainingVerificationCode() {
            SendVerificationMailCommand command =
                    new SendVerificationMailCommand("river@modudrive.com", "042917");

            sendVerificationMailService.sendVerificationMail(command);

            then(sendMailPort).should().send(
                    eq("river@modudrive.com"),
                    contains("인증"),
                    contains("인증 코드: 042917"));
        }
    }
}
