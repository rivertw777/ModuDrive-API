package com.moduDrive.common.infrastructure.sqs;

/** Turns a logical queue name ({@code common:event}) into the physical one SQS knows. Every queue
 * here is FIFO, and SQS requires that suffix in the name. */
public final class SqsQueues {

    /** For {@code @SqsListener(SomeQueues.X + SqsQueues.FIFO_SUFFIX)} — a listener needs the
     * physical name as a compile-time constant. */
    public static final String FIFO_SUFFIX = ".fifo";

    private SqsQueues() {
    }

    static String physicalName(String queue) {
        return queue + FIFO_SUFFIX;
    }
}
