package com.moduDrive.notification.application.service;

import com.moduDrive.notification.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.notification.application.port.out.SendMailPort;
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
class SendShareInviteMailServiceTest {

    @Mock
    private SendMailPort sendMailPort;
    @InjectMocks
    private SendShareInviteMailService sendShareInviteMailService;

    @Nested
    @DisplayName("공유 초대 메일 발송을 요청받았을 때")
    class WhenRequested {

        @Test
        void sendsMailContainingFileNameAndRole() {
            SendShareInviteMailCommand command =
                    new SendShareInviteMailCommand("grantee@modudrive.com", "report.pdf", "VIEWER");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().send(
                    eq("grantee@modudrive.com"), contains("공유"), contains("report.pdf"));
        }
    }
}
