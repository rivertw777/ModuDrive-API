package com.moduDrive.common.infrastructure.sqs;

/** Message attributes this adapter sets itself, because SQS has no field for them on a standard queue. */
public final class SqsAttributes {

    /**
     * Carries the deduplication id ({@code outbox-<row id>}): the same value however often the outbox
     * resends a row, so consumers can tell a resend from a new event. A FIFO queue would have a system
     * attribute for this; a standard queue doesn't, and its own message id changes per send.
     */
    public static final String DEDUPLICATION_ID = "DeduplicationId";

    private SqsAttributes() {
    }
}
