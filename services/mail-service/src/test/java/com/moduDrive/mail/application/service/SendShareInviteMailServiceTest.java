package com.moduDrive.mail.application.service;

import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.out.SendMailPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SendShareInviteMailServiceTest {

    private static final String CLIENT_URL = "http://localhost:3000";

    @Mock
    private SendMailPort sendMailPort;
    private SendShareInviteMailService sendShareInviteMailService;

    @BeforeEach
    void setUp() {
        sendShareInviteMailService = new SendShareInviteMailService(sendMailPort, CLIENT_URL);
    }

    @Nested
    @DisplayName("등록된 회원에게 공유 초대 메일 발송을 요청받았을 때")
    class WhenRequestedForAMember {

        @Test
        void sendsMailContainingFileNameAndRoleWithoutALink() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", "VIEWER", UUID.randomUUID(), null);

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().send(
                    eq("grantee@modudrive.com"), contains("공유"), contains("report.pdf"));
        }
    }

    @Nested
    @DisplayName("회원이 아닌 이메일로 게스트 공유 초대 메일 발송을 요청받았을 때")
    class WhenRequestedForAGuest {

        @Test
        void sendsMailContainingTheNoLoginLink() {
            UUID fileId = UUID.randomUUID();
            UUID linkToken = UUID.randomUUID();
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", "VIEWER", fileId, linkToken);

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().send(
                    eq("grantee@modudrive.com"), contains("공유"),
                    contains(CLIENT_URL + "/public/" + fileId + "?key=" + linkToken));
        }
    }
}
