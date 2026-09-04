package com.moduDrive.mail.adapter.in.messaging;

import com.moduDrive.common.event.mail.ShareInviteMailRequested;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MailEventListenerTest {

    @Mock
    private SendVerificationMailUseCase sendVerificationMailUseCase;
    @Mock
    private SendShareInviteMailUseCase sendShareInviteMailUseCase;
    @InjectMocks
    private MailEventListener mailEventListener;

    @Nested
    @DisplayName("인증 메일 요청 이벤트를 수신했을 때")
    class WhenVerificationRequestedReceived {

        @Test
        void delegatesToSendVerificationMailUseCase() {
            VerificationMailRequested event =
                    new VerificationMailRequested("river@modudrive.com", "042917");

            mailEventListener.onVerificationRequested(event);

            then(sendVerificationMailUseCase).should().sendVerificationMail(
                    new SendVerificationMailCommand("river@modudrive.com", "042917"));
        }
    }

    @Nested
    @DisplayName("공유 초대 메일 요청 이벤트를 수신했을 때")
    class WhenShareInviteRequestedReceived {

        @Test
        void delegatesToSendShareInviteMailUseCase() {
            UUID fileId = UUID.randomUUID();
            UUID linkToken = UUID.randomUUID();
            ShareInviteMailRequested event = new ShareInviteMailRequested(
                    fileId, "grantee@modudrive.com", "report.pdf", "VIEWER", linkToken);

            mailEventListener.onShareInviteRequested(event);

            then(sendShareInviteMailUseCase).should().sendShareInviteMail(
                    new SendShareInviteMailCommand("grantee@modudrive.com", "report.pdf", "VIEWER", fileId, linkToken));
        }
    }
}
