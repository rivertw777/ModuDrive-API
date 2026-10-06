package com.moduDrive.mail.application.port.out;

/** This mail can't be sent as it is: the provider refused this request (unverified sender, sending
 * paused...) or it couldn't even be built. Sending the same mail again fails the same way, so it's not
 * worth a retry — it needs a fix and a redrive. */
public class MailRejectedException extends RuntimeException {

    /** The reason is what the DLQ and the logs show, so it names the refusal (e.g.
     * {@code MessageRejectedException: MessageRejected}) without the provider's own message or cause:
     * those can quote the recipient's address. */
    public MailRejectedException(String reason) {
        super(reason);
    }
}
