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
import com.moduDrive.common.infrastructure.sqs.SqsQueues;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;

@EventListener
@RequiredArgsConstructor
class MailEventListener {

    private final SendVerificationMailUseCase sendVerificationMailUseCase;
    private final SendShareInviteMailUseCase sendShareInviteMailUseCase;
    private final ProcessedEvents processedEvents;

    // Recorded after the send, not before: a mail can't be rolled back, and dying between the two
    // only risks one duplicate mail, while recording first would risk losing the mail entirely.
    @SqsListener(MailQueues.VERIFICATION_REQUESTED + SqsQueues.FIFO_SUFFIX)
    void onVerificationRequested(VerificationMailRequested event,
                                 @Header(MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER) String deduplicationId) {
        if (processedEvents.isProcessed(MailQueues.VERIFICATION_REQUESTED, deduplicationId)) {
            return;
        }
        sendVerificationMailUseCase.sendVerificationMail(
                new SendVerificationMailCommand(event.email(), event.verificationCode()));
        processedEvents.markProcessed(MailQueues.VERIFICATION_REQUESTED, deduplicationId);
    }

    @SqsListener(MailQueues.SHARE_INVITE_REQUESTED + SqsQueues.FIFO_SUFFIX)
    void onShareInviteRequested(ShareInviteMailRequested event,
                                @Header(MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER) String deduplicationId) {
        if (processedEvents.isProcessed(MailQueues.SHARE_INVITE_REQUESTED, deduplicationId)) {
            return;
        }
        sendShareInviteMailUseCase.sendShareInviteMail(
                new SendShareInviteMailCommand(event.granteeEmail(), event.fileName(), event.directory(),
                        event.category(), event.role(), event.fileId(), event.granterName(), event.granterEmail(),
                        event.message(), event.inviteToken()));
        processedEvents.markProcessed(MailQueues.SHARE_INVITE_REQUESTED, deduplicationId);
    }
}
