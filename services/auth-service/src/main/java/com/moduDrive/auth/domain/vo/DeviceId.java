package com.moduDrive.auth.domain.vo;

/** The browser's long-lived id from its device cookie — a login from a device the member already
 * verified by email skips the code (spec 004 2-2). */
public record DeviceId(String value) {

    // Only useful together with the password, but still kept out of logs like the session id.
    @Override
    public String toString() {
        return "DeviceId[***]";
    }
}
