package com.moduDrive.common.infrastructure.sqs;

import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Splits failures into permanent ones (retrying can't fix them) and everything else. Unknown failures
 * count as retryable: a pointless retry costs a few seconds, a wrongly dead-lettered message costs a
 * manual redrive.
 */
public final class SqsFailures {

    // Matched by name up each class hierarchy so this module needs no validation/DAO/Jackson deps.
    // ponytail: fixed list; make it configurable if a service ever needs its own permanent types.
    private static final Set<String> PERMANENT_CONSUMER_FAILURES = Set.of(
            // Payload can't become the listener's parameter type. A body that isn't JSON at all never gets
            // here: Spring Cloud AWS drops it in the message source, so it reaches the DLQ by redrive.
            "org.springframework.messaging.converter.MessageConversionException",
            "tools.jackson.core.JacksonException",
            "jakarta.validation.ValidationException", // SelfValidating command rejected the payload
            "org.springframework.dao.DataIntegrityViolationException",
            "java.lang.IllegalArgumentException",
            "java.lang.NullPointerException",
            "java.lang.ClassCastException");

    private SqsFailures() {
    }

    /** A listener failure the same message would hit again on every retry. */
    public static boolean isPermanentConsumerFailure(Throwable failure) {
        return findCause(failure, cause -> isOneOf(cause.getClass(), PERMANENT_CONSUMER_FAILURES)) != null;
    }

    /**
     * A send that SQS rejected because of this message (queue missing, body too large, bad group id):
     * a 400 that isn't throttling. Outages, 5xx, throttling and access denied are retryable, since they
     * fail every message alike and clear up on their own or with a config fix.
     */
    public static boolean isPermanentSendFailure(Throwable failure) {
        return findCause(failure, cause -> cause instanceof AwsServiceException aws
                && aws.statusCode() == 400 && !aws.isThrottlingException()) != null;
    }

    /** The first throwable in the cause chain matching the test, or null. */
    static Throwable findCause(Throwable failure, Predicate<Throwable> test) {
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
