package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.auth.domain.vo.MemberPassword;

public interface AuthenticateMemberPort {
    MemberAuthData authenticateMember(MemberEmail memberEmail, MemberPassword memberPassword);
}
