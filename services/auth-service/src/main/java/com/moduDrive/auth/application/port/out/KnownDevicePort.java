package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.MemberEmail;

/** Devices each member has verified by email (spec 004 2-1), forgotten after 90 days unused. */
public interface KnownDevicePort {

    /** True when this email verified this device within the 90 days. Read-only, and asked before the
     * password is checked — it only picks which attempt count a login goes to (spec 004 2-2). */
    boolean isKnown(MemberEmail memberEmail, DeviceId deviceId);

    /** True when the member verified this device before; also restarts its 90 days. */
    boolean refreshIfKnown(String memberId, MemberEmail memberEmail, DeviceId deviceId);

    /** Records the device as verified for the member — minting a new id when there's none — and returns it. */
    DeviceId remember(String memberId, MemberEmail memberEmail, DeviceId deviceId);
}
