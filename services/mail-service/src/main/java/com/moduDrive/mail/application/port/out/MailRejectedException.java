package com.moduDrive.mail.application.port.out;

/** The provider refused the mail (unverified sender, sending paused...) or it couldn't be built. A retry
 * fails the same way, so it needs a fix and a redrive. */
public class MailRejectedException extends RuntimeException {

    /** Shown in the DLQ and logs. Names the refusal (e.g. {@code MessageRejectedException: MessageRejected})
     * without the provider's message or cause, which can quote the recipient's address. */
    public MailRejectedException(String reason) {
        super(reason);
    }
}
