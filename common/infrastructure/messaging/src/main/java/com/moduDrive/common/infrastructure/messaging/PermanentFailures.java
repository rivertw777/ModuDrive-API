package com.moduDrive.common.infrastructure.messaging;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Tells a failure that retrying can fix from one that it can't, for consumers of any broker. Unknown
 * failures count as retryable: a pointless retry costs a few seconds, a wrongly dead-lettered message
 * costs a manual redrive.
 */
public final class PermanentFailures {

    // Matched by name up each class hierarchy so this module needs no validation/DAO/Jackson deps.
    // ponytail: fixed list; make it configurable if a service ever needs its own permanent types.
    private static final Set<String> PERMANENT = Set.of(
            // Payload can't become the listener's parameter type. A body that isn't JSON at all never
            // gets here: the broker's client drops it before the listener runs.
            "org.springframework.messaging.converter.MessageConversionException",
            "tools.jackson.core.JacksonException",
            "jakarta.validation.ValidationException", // SelfValidating command rejected the payload
            "org.springframework.dao.DataIntegrityViolationException",
            "java.lang.IllegalArgumentException",
            "java.lang.NullPointerException",
            "java.lang.ClassCastException");

    private PermanentFailures() {
    }

    /** A listener failure the same message would hit again on every retry. */
    public static boolean isPermanent(Throwable failure) {
        return findCause(failure, cause -> isOneOf(cause.getClass())) != null;
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

    private static boolean isOneOf(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (PERMANENT.contains(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
