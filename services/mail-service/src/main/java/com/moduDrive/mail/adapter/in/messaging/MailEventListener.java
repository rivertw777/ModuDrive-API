package com.moduDrive.mail.adapter.in.messaging;

import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.mail.MailQueues;
import com.moduDrive.common.event.mail.ShareInviteMailRequested;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;

@EventListener
@RequiredArgsConstructor
class MailEventListener {

    private final SendVerificationMailUseCase sendVerificationMailUseCase;
    private final SendShareInviteMailUseCase sendShareInviteMailUseCase;

    @SqsListener(MailQueues.VERIFICATION_REQUESTED)
    void onVerificationRequested(VerificationMailRequested event) {
        sendVerificationMailUseCase.sendVerificationMail(
                new SendVerificationMailCommand(event.email(), event.verificationCode()));
    }

    @SqsListener(MailQueues.SHARE_INVITE_REQUESTED)
    void onShareInviteRequested(ShareInviteMailRequested event) {
        sendShareInviteMailUseCase.sendShareInviteMail(
                new SendShareInviteMailCommand(event.granteeEmail(), event.fileName(), event.directory(),
                        event.category(), event.role(), event.fileId(), event.granterName(), event.granterEmail(),
                        event.message(), event.inviteToken()));
    }
}
