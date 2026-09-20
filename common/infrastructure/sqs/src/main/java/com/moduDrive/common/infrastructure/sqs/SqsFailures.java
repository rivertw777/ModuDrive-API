package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.infrastructure.messaging.PermanentFailures;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/** SQS-specific half of failure classification; the broker-agnostic half is {@link PermanentFailures}. */
final class SqsFailures {

    private SqsFailures() {
    }

    /**
     * A send that can only ever fail: SQS rejected this message (queue missing, body too large, bad
     * group id — a 400 that isn't throttling), or it never got that far because the event wouldn't
     * serialize. Outages, 5xx, throttling and access denied are retryable, since they fail every
     * message alike and clear up on their own or with a config fix.
     * <p>
     * The serialization half matters because the relay stops its batch on a retryable failure: without
     * it one unserializable row would block every row behind it for good.
     */
    static boolean isPermanentSendFailure(Throwable failure) {
        return PermanentFailures.isPermanentForPublisher(failure)
                || PermanentFailures.findCause(failure, cause -> cause instanceof AwsServiceException aws
                && aws.statusCode() == 400 && !aws.isThrottlingException()) != null;
    }
}
