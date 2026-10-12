package com.moduDrive.common.infrastructure.messaging;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Tells a failure that retrying can fix from one it can't, for any broker. Unknown failures count as
 * retryable: a pointless retry costs seconds, a wrongly discarded message costs a manual redrive.
 * The same exception type means different things on the consume and publish sides, hence two methods.
 */
public final class PermanentFailures {

    // Matched by name up each class hierarchy so this module needs no validation/DAO/Jackson deps.
    /** The body won't convert to the listener's parameter type, or the event won't serialize. */
    private static final Set<String> CONVERSION = Set.of(
            "org.springframework.messaging.converter.MessageConversionException",
            "tools.jackson.core.JacksonException");

    /** Business code rejecting the message it was handed; consumer side only.
     * {@code DataIntegrityViolationException} is deliberately absent: on a standard queue two copies of one
     * message can be handled at once, and the loser fails on {@code uk_processed_event}. Retrying finds the
     * claim taken and skips it; calling it permanent would fill the DLQ with correctly handled messages. */
    private static final Set<String> LISTENER_ONLY = Set.of(
            "jakarta.validation.ValidationException", // SelfValidating command rejected the payload
            "java.lang.IllegalArgumentException",
            "java.lang.NullPointerException",
            "java.lang.ClassCastException",
            "com.moduDrive.common.infrastructure.messaging.PermanentConsumeException"); // downstream refused it

    private PermanentFailures() {
    }

    /** The payload can't be read or the business code rejected it, so redelivery changes nothing. */
    public static boolean isPermanentForConsumer(Throwable failure) {
        return findCause(failure, cause -> isOneOf(cause.getClass(), CONVERSION)
                || isOneOf(cause.getClass(), LISTENER_ONLY)) != null;
    }

    /**
     * The event won't serialize. The broker adapter adds its own cases (for SQS, a 400 that isn't
     * throttling). Narrower than {@link #isPermanentForConsumer} on purpose: on the send path the SDK
     * throws {@code IllegalArgumentException} for trouble unrelated to the row, and parking on it would
     * discard healthy events during an outage.
     */
    public static boolean isPermanentForPublisher(Throwable failure) {
        return findCause(failure, cause -> isOneOf(cause.getClass(), CONVERSION)) != null;
    }

    /** The first throwable in the cause chain matching the test, or null. */
    public static Throwable findCause(Throwable failure, Predicate<Throwable> test) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (test.test(cause)) {
                return cause;
            }
        }
        return null;
    }

    private static boolean isOneOf(Class<?> type, Set<String> names) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (names.contains(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
