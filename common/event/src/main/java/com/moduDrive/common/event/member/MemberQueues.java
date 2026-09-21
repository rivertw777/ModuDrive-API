package com.moduDrive.common.event.member;

/** Queue names shared by member-service (producer) and its consumers (e.g. file-service).
 * The queue name as the broker knows it. */
public final class MemberQueues {
    public static final String SIGNED_UP = "member-signed-up";
    private MemberQueues() {}
}
