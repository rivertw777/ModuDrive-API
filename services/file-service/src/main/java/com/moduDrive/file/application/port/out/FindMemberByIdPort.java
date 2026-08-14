package com.moduDrive.file.application.port.out;

import java.util.UUID;

public interface FindMemberByIdPort {

    /** Resolves a member id to display info for share-list enrichment (see #156). */
    MemberSummary findMemberById(UUID memberId);

    record MemberSummary(String name, String email) {}
}
