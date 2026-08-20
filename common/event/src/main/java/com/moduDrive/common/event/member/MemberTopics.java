package com.moduDrive.common.event.member;

/** Kafka topic names shared by member-service (producer) and its consumers (e.g. file-service). */
public final class MemberTopics {
    public static final String SIGNED_UP = "member.signed-up";
    private MemberTopics() {}
}
