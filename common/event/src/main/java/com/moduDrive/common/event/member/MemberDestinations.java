package com.moduDrive.common.event.member;

/** Destination names shared by member-service (producer) and its consumers (e.g. file-service).
 * Logical names, without a broker's own naming rules — the adapter maps them (SQS appends {@code .fifo}). */
public final class MemberDestinations {
    public static final String SIGNED_UP = "member-signed-up";
    private MemberDestinations() {}
}
