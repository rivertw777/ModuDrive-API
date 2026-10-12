package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.infrastructure.messaging.PermanentFailures;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/** SQS-specific half of failure classification; the broker-agnostic half is {@link PermanentFailures}. */
final class SqsFailures {

    private SqsFailures() {
    }

    /**
     * SQS rejected this message (a 400 that isn't throttling: queue missing, body too large), or the event
     * won't serialize. Outages, 5xx, throttling and access denied are retryable: they fail every message
     * alike and clear up on their own or with a config fix.
     */
    static boolean isPermanentSendFailure(Throwable failure) {
        return PermanentFailures.isPermanentForPublisher(failure)
                || PermanentFailures.findCause(failure, cause -> cause instanceof AwsServiceException aws
                && aws.statusCode() == 400 && !aws.isThrottlingException()) != null;
    }
}
