package com.moduDrive.common.infrastructure.messaging;

/** Thrown by a consumer when the downstream it calls refused this exact request, so retrying would fail the
 * same way. Sends the message straight to the DLQ. Consumer-side twin of {@link PermanentPublishException}. */
public class PermanentConsumeException extends RuntimeException {

    public PermanentConsumeException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
