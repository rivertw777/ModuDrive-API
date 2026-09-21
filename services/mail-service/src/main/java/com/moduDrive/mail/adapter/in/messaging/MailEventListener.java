package com.moduDrive.mail.adapter.in.messaging;

import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.mail.MailQueues;
import com.moduDrive.common.event.mail.ShareInviteMailRequested;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import com.moduDrive.common.infrastructure.sqs.SqsAttributes;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;

@EventListener
@RequiredArgsConstructor
class MailEventListener {

    private final SendVerificationMailUseCase sendVerificationMailUseCase;
    private final SendShareInviteMailUseCase sendShareInviteMailUseCase;
    private final ProcessedEvents processedEvents;

    // Claim, send, then confirm. A mail can't be rolled back, so the claim goes first — two copies of
    // one message can be in flight at once and a read-then-write would let both send. It only holds a
    // short lease, so a process dying mid-send leaves the retry free to take it: a second mail is
    // better than none. A send that fails hands the claim straight back.
    @SqsListener(MailQueues.VERIFICATION_REQUESTED)
    void onVerificationRequested(VerificationMailRequested event,
                                 @Header(SqsAttributes.DEDUPLICATION_ID) String deduplicationId) {
        if (!processedEvents.claim(MailQueues.VERIFICATION_REQUESTED, deduplicationId)) {
            return;
        }
        try {
            sendVerificationMailUseCase.sendVerificationMail(
                    new SendVerificationMailCommand(event.email(), event.verificationCode()));
        } catch (RuntimeException e) {
            processedEvents.release(MailQueues.VERIFICATION_REQUESTED, deduplicationId);
            throw e;
        }
        processedEvents.markProcessed(MailQueues.VERIFICATION_REQUESTED, deduplicationId);
    }

    @SqsListener(MailQueues.SHARE_INVITE_REQUESTED)
    void onShareInviteRequested(ShareInviteMailRequested event,
                                @Header(SqsAttributes.DEDUPLICATION_ID) String deduplicationId) {
        if (!processedEvents.claim(MailQueues.SHARE_INVITE_REQUESTED, deduplicationId)) {
            return;
        }
        try {
            sendShareInviteMailUseCase.sendShareInviteMail(
                    new SendShareInviteMailCommand(event.granteeEmail(), event.fileName(), event.directory(),
                            event.category(), event.role(), event.fileId(), event.granterName(), event.granterEmail(),
                            event.message(), event.inviteToken()));
        } catch (RuntimeException e) {
            processedEvents.release(MailQueues.SHARE_INVITE_REQUESTED, deduplicationId);
            throw e;
        }
        processedEvents.markProcessed(MailQueues.SHARE_INVITE_REQUESTED, deduplicationId);
    }
}
