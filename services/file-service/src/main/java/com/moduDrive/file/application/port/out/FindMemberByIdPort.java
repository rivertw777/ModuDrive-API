package com.moduDrive.file.application.port.out;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public interface FindMemberByIdPort {

    Logger LOG = LoggerFactory.getLogger(FindMemberByIdPort.class);
    MemberSummary UNKNOWN_MEMBER = new MemberSummary(null, null);

    /** Display info for a member id. */
    MemberSummary findMemberById(UUID memberId);

    /** Best-effort: a failed lookup (member-service down, member gone) degrades to
     * {@link #UNKNOWN_MEMBER} instead of failing the listing that only needed display data.
     * Never returns null. */
    default MemberSummary findMemberByIdOrUnknown(UUID memberId) {
        MemberSummary found;
        try {
            found = findMemberById(memberId);
        } catch (RuntimeException e) {
            LOG.warn("Failed to resolve member {}, showing as unknown", memberId, e);
            return UNKNOWN_MEMBER;
        }
        return found != null ? found : UNKNOWN_MEMBER;
    }

    record MemberSummary(String name, String email) {}
}
