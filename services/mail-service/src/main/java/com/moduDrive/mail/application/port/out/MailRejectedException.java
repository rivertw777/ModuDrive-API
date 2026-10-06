package com.moduDrive.mail.application.port.out;

/** This mail can't be sent as it is: the provider refused this request (unverified sender, sending
 * paused...) or it couldn't even be built. Sending the same mail again fails the same way, so it's not
 * worth a retry — it needs a fix and a redrive. */
public class MailRejectedException extends RuntimeException {

    /** Keeps the cause's type in the message (e.g. {@code MessageRejectedException: ...}) — SES's own
     * message alone often doesn't say which refusal it was, and this is what the DLQ reason shows. */
    public MailRejectedException(Throwable cause) {
        super(cause.toString(), cause);
    }
}
