package com.moduDrive.common.infrastructure.messaging;

/** The consumer's own verdict that this message can never be handled — the downstream it calls refused
 * this exact request, not just this moment. Sends it straight to the DLQ instead of burning retries that
 * would fail the same way. The consumer-side twin of {@link PermanentPublishException}. */
public class PermanentConsumeException extends RuntimeException {

    public PermanentConsumeException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
