package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.vo.DeviceId;

/** Devices each member has verified by email (spec 004 2-2), forgotten after 90 days unused. */
public interface KnownDevicePort {

    /** True when the member verified this device before; also restarts its 90 days. */
    boolean refreshIfKnown(String memberId, DeviceId deviceId);

    /** Records the device as verified for the member — minting a new id when there's none — and returns it. */
    DeviceId remember(String memberId, DeviceId deviceId);
}
