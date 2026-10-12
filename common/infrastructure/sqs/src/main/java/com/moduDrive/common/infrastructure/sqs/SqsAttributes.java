package com.moduDrive.common.infrastructure.sqs;

/** Message attributes this adapter sets itself, because SQS has no field for them on a standard queue. */
public final class SqsAttributes {

    /** The outbox deduplication id ({@code outbox-<row id>}), the same on every resend of a row. The SQS
     * message id changes per send. */
    public static final String DEDUPLICATION_ID = "DeduplicationId";

    private SqsAttributes() {
    }
}
