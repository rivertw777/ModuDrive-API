package com.moduDrive.member.application.port.out;

import com.moduDrive.member.domain.model.Member;

public interface SignUpMemberPort {
    Member createMember(Member member);
}
