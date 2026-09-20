package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.infrastructure.messaging.PermanentFailures;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/** SQS-specific half of failure classification; the broker-agnostic half is {@link PermanentFailures}. */
final class SqsFailures {

    private SqsFailures() {
    }

    /**
     * A send that SQS rejected because of this message (queue missing, body too large, bad group id):
     * a 400 that isn't throttling. Outages, 5xx, throttling and access denied are retryable, since they
     * fail every message alike and clear up on their own or with a config fix.
     */
    static boolean isPermanentSendFailure(Throwable failure) {
        return PermanentFailures.findCause(failure, cause -> cause instanceof AwsServiceException aws
                && aws.statusCode() == 400 && !aws.isThrottlingException()) != null;
    }
}
