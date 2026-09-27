package com.moduDrive.auth.domain.model;

import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.SessionId;

/** A login either signs in, or — on a device the member hasn't verified — waits for the emailed code (spec 004 2-1). */
public sealed interface LoginResult {

    /** {@code deviceId} goes back in the device cookie so its lifetime restarts. */
    record SignedIn(SessionId sessionId, DeviceId deviceId) implements LoginResult {
    }

    record VerificationRequired(LoginChallengeId challengeId) implements LoginResult {
    }
}
