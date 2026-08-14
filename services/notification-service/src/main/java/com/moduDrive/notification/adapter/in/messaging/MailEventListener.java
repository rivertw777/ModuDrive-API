package com.moduDrive.notification.adapter.in.messaging;

import com.moduDrive.common.event.mail.MailTopics;
import com.moduDrive.common.event.mail.ShareInviteMailRequested;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.notification.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.notification.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.notification.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.notification.application.port.in.usecase.SendVerificationMailUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MailEventListener {

    private final SendVerificationMailUseCase sendVerificationMailUseCase;
    private final SendShareInviteMailUseCase sendShareInviteMailUseCase;

    @KafkaListener(topics = MailTopics.VERIFICATION_REQUESTED)
    void onVerificationRequested(VerificationMailRequested event) {
        sendVerificationMailUseCase.sendVerificationMail(
                new SendVerificationMailCommand(event.email(), event.verificationToken()));
    }

    @KafkaListener(topics = MailTopics.SHARE_INVITE_REQUESTED)
    void onShareInviteRequested(ShareInviteMailRequested event) {
        sendShareInviteMailUseCase.sendShareInviteMail(
                new SendShareInviteMailCommand(event.granteeEmail(), event.fileName(), event.role()));
    }
}
