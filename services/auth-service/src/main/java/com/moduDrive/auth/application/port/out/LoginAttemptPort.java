package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.vo.MemberEmail;

public interface LoginAttemptPort {

    /** Counts a login attempt for this email; false once the email has used up its attempts. */
    boolean tryAttempt(MemberEmail memberEmail);

    void clearAttempts(MemberEmail memberEmail);
}
