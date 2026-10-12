package com.moduDrive.mail.adapter.in.messaging;

import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.auth.AuthQueues;
import com.moduDrive.common.event.auth.LoginVerificationMailRequested;
import com.moduDrive.common.event.file.FileQueues;
import com.moduDrive.common.event.file.ShareInviteMailRequested;
import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.event.member.SignUpVerificationMailRequested;
import com.moduDrive.common.infrastructure.messaging.PermanentConsumeException;
import com.moduDrive.common.infrastructure.messaging.RetryLaterException;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.common.infrastructure.sqs.SqsAttributes;
import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand.Purpose;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import com.moduDrive.mail.application.port.out.MailOutcomeUnknownException;
import com.moduDrive.mail.application.port.out.MailRejectedException;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.function.Consumer;

@EventListener
@RequiredArgsConstructor
class MailEventListener {

    private final SendVerificationMailUseCase sendVerificationMailUseCase;
    private final SendShareInviteMailUseCase sendShareInviteMailUseCase;
    /** SES Send events (SES → SNS → this queue). Not in common:event because SES produces it. */
    static final String SES_EVENTS_QUEUE = "mail-ses-events";
    // Must match SesMailAdapter.DELIVERY_TAG; adapters don't reference each other.
    private static final String DELIVERY_TAG = "deliveryId";
    /** How long a timed-out send waits for its Send event before it is retried. SES publishes the event
     * within seconds of accepting a mail; this leaves room for a slow accept plus the SNS hop. */
    static final Duration SEND_EVENT_WAIT = Duration.ofSeconds(60);

    private final ProcessedEvents processedEvents;
    private final JsonMapper jsonMapper;

    @SqsListener(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED)
    void onSignUpVerificationRequested(SignUpVerificationMailRequested event,
                                       @Header(SqsAttributes.DEDUPLICATION_ID) String deduplicationId) {
        sendOnce(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, deduplicationId, deliveryId ->
                sendVerificationMailUseCase.sendVerificationMail(
                        new SendVerificationMailCommand(event.email(), event.verificationCode(), Purpose.SIGN_UP, deliveryId)));
    }

    @SqsListener(AuthQueues.LOGIN_VERIFICATION_MAIL_REQUESTED)
    void onLoginVerificationRequested(LoginVerificationMailRequested event,
                                      @Header(SqsAttributes.DEDUPLICATION_ID) String deduplicationId) {
        sendOnce(AuthQueues.LOGIN_VERIFICATION_MAIL_REQUESTED, deduplicationId, deliveryId ->
                sendVerificationMailUseCase.sendVerificationMail(
                        new SendVerificationMailCommand(event.email(), event.verificationCode(), Purpose.LOGIN, deliveryId)));
    }

    @SqsListener(FileQueues.SHARE_INVITE_MAIL_REQUESTED)
    void onShareInviteRequested(ShareInviteMailRequested event,
                                @Header(SqsAttributes.DEDUPLICATION_ID) String deduplicationId) {
        sendOnce(FileQueues.SHARE_INVITE_MAIL_REQUESTED, deduplicationId, deliveryId ->
                sendShareInviteMailUseCase.sendShareInviteMail(
                        new SendShareInviteMailCommand(event.granteeEmail(), event.fileName(), event.directory(),
                                event.category(), event.fileId(), event.granterName(), event.granterEmail(),
                                event.message(), event.inviteToken(), deliveryId)));
    }

    /**
     * A Send event means SES accepted the mail. Marking it processed makes a redelivery of a timed-out
     * send skip it (see {@link #sendOnce}). Delivered raw (no SNS envelope); anything that isn't a tagged
     * Send event is ignored, including SNS's "Successfully validated" text on subscribe.
     */
    @SqsListener(SES_EVENTS_QUEUE)
    void onSesEvent(String body) {
        if (!body.startsWith("{")) {
            return;
        }
        JsonNode event = jsonMapper.readTree(body);
        String deliveryId = event.path("mail").path("tags").path(DELIVERY_TAG).path(0).asString(null);
        if (!"Send".equals(event.path("eventType").asString(null)) || deliveryId == null) {
            return;
        }
        int split = deliveryId.indexOf('_');
        if (split <= 0) {
            return;
        }
        processedEvents.markProcessed(deliveryId.substring(0, split), deliveryId.substring(split + 1));
    }

    // Claim, send, then mark processed. The claim goes first because two copies of one message can be
    // in flight at once and a mail can't be rolled back. The claim is a short lease, so if the process
    // dies mid-send the retry can take it (a second mail is better than none). A failed send releases it.
    //
    // A timed-out send may still go out, so the claim is left to lapse and the message comes back after
    // SEND_EVENT_WAIT. If the Send event arrived meanwhile, the retry skips it; otherwise it sends again.
    // A mail SES refused is not retried.
    private void sendOnce(String queue, String deduplicationId, Consumer<String> send) {
        if (!processedEvents.claim(queue, deduplicationId)) {
            return;
        }
        try {
            // SES echoes this tag in the Send event. Tag values allow only [A-Za-z0-9_-]; queue names and
            // outbox ids use only [a-z0-9-], so '_' splits it back unambiguously.
            send.accept(queue + "_" + deduplicationId);
        } catch (MailOutcomeUnknownException e) {
            throw new RetryLaterException(SEND_EVENT_WAIT, e);
        } catch (MailRejectedException e) {
            // Would be refused on every retry, so straight to the DLQ. Release the claim so a redrive can send it.
            processedEvents.release(queue, deduplicationId);
            throw new PermanentConsumeException(e);
        } catch (RuntimeException e) {
            processedEvents.release(queue, deduplicationId);
            throw e;
        }
        processedEvents.markProcessed(queue, deduplicationId);
    }
}
