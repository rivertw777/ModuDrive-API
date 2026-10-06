package com.moduDrive.common.infrastructure.messaging;

import java.time.Duration;

/** A consumer that can't tell yet whether its work took effect — a call that timed out may still have
 * gone through. Redeliver the message no sooner than {@link #delay()}, by which time the answer should
 * be known, instead of on the usual short backoff that would just repeat the work. */
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
