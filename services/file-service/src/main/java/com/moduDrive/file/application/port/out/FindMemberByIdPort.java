package com.moduDrive.file.application.port.out;

import java.util.UUID;

public interface FindMemberByIdPort {

    MemberSummary UNKNOWN_MEMBER = new MemberSummary(null, null);

    /** Display info for a member id. Best-effort: a failed lookup (member-service down, member gone)
     * degrades to {@link #UNKNOWN_MEMBER} instead of failing the listing that only needed display data.
     * Never returns null. */
    MemberSummary findMemberByIdOrUnknown(UUID memberId);

    record MemberSummary(String name, String email) {}
}
