package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.LoginChallenge;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.MemberEmail;

/** Logins waiting for their emailed code (spec 004 2-2), kept 5 minutes from the login or the last code sent. */
public interface LoginChallengePort {

    /** Starts a challenge with no code yet — the member asks for one with {@link #issueCode}. */
    LoginChallengeId createChallenge(MemberAuthData memberAuthData, MemberEmail memberEmail);

    /** The email the waiting login was made with. Throws {@code LOGIN_VERIFICATION_EXPIRED} when there is none. */
    MemberEmail findEmail(LoginChallengeId challengeId);

    /** Counts one code request for the address, unless it's within the resend cooldown (not counted). */
    CodeRequest requestCode(MemberEmail memberEmail);

    /**
     * Replaces any earlier code with {@code code}, starting its wrong-code count and the challenge's
     * 5 minutes over. Throws {@code LOGIN_VERIFICATION_EXPIRED} when there is no waiting login.
     */
    void issueCode(LoginChallengeId challengeId, String code);

    /**
     * Returns the waiting login and ends it when {@code code} matches. Throws
     * {@code LOGIN_VERIFICATION_EXPIRED} when there is none, {@code INVALID_LOGIN_VERIFICATION_CODE} on a
     * wrong code (or any code before one was sent), {@code LOGIN_VERIFICATION_ATTEMPTS_EXCEEDED}
     * from the fifth wrong code on — the challenge stays, so a resend is enough. When the code ends,
     * matched or used up, the resend cooldown ends with it.
     */
    LoginChallenge confirmChallenge(LoginChallengeId challengeId, String code);

    enum CodeRequest {
        ALLOWED,
        /** Within the resend cooldown after the address's last code. */
        TOO_SOON,
        /** The address has asked too often in the window. */
        TOO_MANY
    }
}
