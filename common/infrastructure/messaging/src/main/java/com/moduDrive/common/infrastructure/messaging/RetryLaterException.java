package com.moduDrive.common.infrastructure.messaging;

import java.time.Duration;

/** Thrown when a consumer can't tell yet whether its work took effect (a timed-out call may still have
 * gone through). The message is redelivered after {@link #delay()} instead of the short backoff. */
public class RetryLaterException extends RuntimeException {

    private final Duration delay;

    public RetryLaterException(Duration delay, Throwable cause) {
        super("Retry no sooner than " + delay, cause);
        this.delay = delay;
    }

    public Duration delay() {
        return delay;
    }
}
