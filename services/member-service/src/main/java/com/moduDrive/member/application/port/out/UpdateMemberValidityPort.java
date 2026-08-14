package com.moduDrive.member.application.port.out;

import com.moduDrive.member.domain.model.Member.MemberId;

public interface UpdateMemberValidityPort {
    void markEmailVerified(MemberId memberId);
}
