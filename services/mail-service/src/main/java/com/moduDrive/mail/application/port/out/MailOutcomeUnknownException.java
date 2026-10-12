package com.moduDrive.mail.application.port.out;

/** The send timed out, so the mail may still go out. Resending could deliver a second copy; the outcome
 * has to be confirmed by delivery id first. */
public class MailOutcomeUnknownException extends RuntimeException {

    public MailOutcomeUnknownException(Throwable cause) {
        super(cause);
    }
}
