package com.moduDrive.mail.application.port.out;

/** The send gave up waiting for an answer, so the mail may or may not have gone out — the provider can
 * still deliver a request the caller stopped waiting for. Sending again could mean a second copy, so
 * this isn't a plain failure: the outcome has to be looked up by the delivery id before deciding. */
public class MailOutcomeUnknownException extends RuntimeException {

    public MailOutcomeUnknownException(Throwable cause) {
        super(cause);
    }
}
