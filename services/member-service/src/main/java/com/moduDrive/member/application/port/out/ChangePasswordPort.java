package com.moduDrive.member.application.port.out;

import com.moduDrive.member.domain.model.Member.*;

public interface ChangePasswordPort {
    void changePassword(MemberId memberId, MemberPassword encodedPassword);
}
