package com.moduDrive.common.event.member;

/** SQS FIFO queue names shared by member-service (producer) and its consumers (e.g. file-service). */
public final class MemberQueues {
    public static final String SIGNED_UP = "member-signed-up.fifo";
    private MemberQueues() {}
}
