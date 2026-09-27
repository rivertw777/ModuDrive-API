package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.LoginChallenge;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.MemberEmail;

/** Logins waiting for their emailed code (spec 004 2-2), kept for 5 minutes. */
public interface LoginChallengePort {

    LoginChallengeId createChallenge(MemberAuthData memberAuthData, MemberEmail memberEmail, String code);

    /**
     * Returns the waiting login and ends it when {@code code} matches. Throws
     * {@code LOGIN_VERIFICATION_EXPIRED} when there is none, {@code INVALID_LOGIN_VERIFICATION_CODE} on a
     * wrong code — the fifth wrong code ends the challenge too.
     */
    LoginChallenge confirmChallenge(LoginChallengeId challengeId, String code);
}
