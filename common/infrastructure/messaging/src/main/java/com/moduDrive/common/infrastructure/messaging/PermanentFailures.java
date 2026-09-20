package com.moduDrive.common.infrastructure.messaging;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Tells a failure that retrying can fix from one that it can't, for any broker. Unknown failures
 * count as retryable: a pointless retry costs a few seconds, a wrongly discarded message costs a
 * manual redrive.
 * <p>
 * The two sides don't share a verdict, so they don't share a method. The same exception type means
 * different things depending on which way the message was going — see
 * {@link #isPermanentForConsumer} and {@link #isPermanentForPublisher}.
 */
public final class PermanentFailures {

    // Matched by name up each class hierarchy so this module needs no validation/DAO/Jackson deps.
    /** The payload and the target type can't be reconciled: the body won't become the listener's
     * parameter type, or the event won't serialize. Means the same thing on both sides. */
    private static final Set<String> CONVERSION = Set.of(
            "org.springframework.messaging.converter.MessageConversionException",
            "tools.jackson.core.JacksonException");

    /** Business code judging the message it was handed. Only a consumer runs that code, so only a
     * consumer reads this list. */
    // ponytail: fixed list; make it configurable if a service ever needs its own permanent types.
    private static final Set<String> LISTENER_ONLY = Set.of(
            "jakarta.validation.ValidationException", // SelfValidating command rejected the payload
            "org.springframework.dao.DataIntegrityViolationException",
            "java.lang.IllegalArgumentException",
            "java.lang.NullPointerException",
            "java.lang.ClassCastException");

    private PermanentFailures() {
    }

    /** A listener failure the same message would hit again on every retry: the payload can't be read,
     * or the business code rejected what it says. Either way, redelivering it changes nothing. */
    public static boolean isPermanentForConsumer(Throwable failure) {
        return findCause(failure, cause -> isOneOf(cause.getClass(), CONVERSION)
                || isOneOf(cause.getClass(), LISTENER_ONLY)) != null;
    }

    /**
     * A send failure the same row would hit again on every retry, as far as the broker-agnostic side
     * can tell — the event won't serialize. The broker adapter adds its own half (for SQS, a 400 that
     * isn't throttling).
     * <p>
     * Deliberately narrower than {@link #isPermanentForConsumer}: a consumer reading
     * {@code IllegalArgumentException} knows the message it was handed is bad, but on the send path
     * the SDK throws that same type for trouble that has nothing to do with this row. Parking a row on
     * one would discard a healthy event during an outage — and the relay retries outages without a
     * limit precisely so that never happens.
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
