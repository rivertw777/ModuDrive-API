package com.moduDrive.mail.adapter.in.messaging;

import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.auth.AuthQueues;
import com.moduDrive.common.event.auth.LoginVerificationMailRequested;
import com.moduDrive.common.event.file.FileQueues;
import com.moduDrive.common.event.file.ShareInviteMailRequested;
import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.event.member.SignUpVerificationMailRequested;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.common.infrastructure.sqs.SqsAttributes;
import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand.Purpose;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;

@EventListener
@RequiredArgsConstructor
class MailEventListener {

    private final SendVerificationMailUseCase sendVerificationMailUseCase;
    private final SendShareInviteMailUseCase sendShareInviteMailUseCase;
    private final ProcessedEvents processedEvents;

    @SqsListener(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED)
    void onSignUpVerificationRequested(SignUpVerificationMailRequested event,
                                       @Header(SqsAttributes.DEDUPLICATION_ID) String deduplicationId) {
        sendOnce(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, deduplicationId, () ->
                sendVerificationMailUseCase.sendVerificationMail(
                        new SendVerificationMailCommand(event.email(), event.verificationCode(), Purpose.SIGN_UP)));
    }

    @SqsListener(AuthQueues.LOGIN_VERIFICATION_MAIL_REQUESTED)
    void onLoginVerificationRequested(LoginVerificationMailRequested event,
                                      @Header(SqsAttributes.DEDUPLICATION_ID) String deduplicationId) {
        sendOnce(AuthQueues.LOGIN_VERIFICATION_MAIL_REQUESTED, deduplicationId, () ->
                sendVerificationMailUseCase.sendVerificationMail(
                        new SendVerificationMailCommand(event.email(), event.verificationCode(), Purpose.LOGIN)));
    }

    @SqsListener(FileQueues.SHARE_INVITE_MAIL_REQUESTED)
    void onShareInviteRequested(ShareInviteMailRequested event,
                                @Header(SqsAttributes.DEDUPLICATION_ID) String deduplicationId) {
        sendOnce(FileQueues.SHARE_INVITE_MAIL_REQUESTED, deduplicationId, () ->
                sendShareInviteMailUseCase.sendShareInviteMail(
                        new SendShareInviteMailCommand(event.granteeEmail(), event.fileName(), event.directory(),
                                event.category(), event.role(), event.fileId(), event.granterName(), event.granterEmail(),
                                event.message(), event.inviteToken())));
    }

    // Claim, send, then confirm. A mail can't be rolled back, so the claim goes first — two copies of
    // one message can be in flight at once and a read-then-write would let both send. It only holds a
    // short lease, so a process dying mid-send leaves the retry free to take it: a second mail is
    // better than none. A send that fails hands the claim straight back.
    private void sendOnce(String queue, String deduplicationId, Runnable send) {
        if (!processedEvents.claim(queue, deduplicationId)) {
            return;
        }
        try {
            send.run();
        } catch (RuntimeException e) {
            processedEvents.release(queue, deduplicationId);
            throw e;
        }
        processedEvents.markProcessed(queue, deduplicationId);
    }
}
