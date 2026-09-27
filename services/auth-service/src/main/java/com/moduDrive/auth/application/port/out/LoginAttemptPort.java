package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.MemberEmail;

/**
 * Login attempts per email (spec 004 2-1). A device the email already verified counts on its own, so
 * someone failing on purpose from elsewhere can't lock the member out of their usual devices;
 * {@code knownDevice} null means the shared count every unknown device and cookie-less login uses.
 */
public interface LoginAttemptPort {

    /** Counts a login attempt; false once that count has used up its attempts. */
    boolean tryAttempt(MemberEmail memberEmail, DeviceId knownDevice);

    void clearAttempts(MemberEmail memberEmail, DeviceId knownDevice);
}
