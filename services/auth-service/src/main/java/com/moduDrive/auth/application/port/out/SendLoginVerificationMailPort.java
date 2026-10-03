package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.vo.MemberEmail;

public interface SendLoginVerificationMailPort {
    /** Has the new-device login code mailed to the member (spec 004 2-1). */
    void sendLoginVerificationMail(MemberEmail memberEmail, String code);
}
