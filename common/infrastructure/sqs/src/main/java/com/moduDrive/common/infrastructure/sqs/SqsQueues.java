package com.moduDrive.common.infrastructure.sqs;

/** Maps a logical destination ({@code common:event}) onto the SQS queue that carries it. Every queue
 * here is FIFO, and SQS requires that suffix in the name. */
public final class SqsQueues {

    /** For {@code @SqsListener(SomeDestinations.X + SqsQueues.FIFO_SUFFIX)} — a listener needs the
     * physical name as a compile-time constant. */
    public static final String FIFO_SUFFIX = ".fifo";

    private SqsQueues() {
    }

    static String queueName(String destination) {
        return destination + FIFO_SUFFIX;
    }
}
