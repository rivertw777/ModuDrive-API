package com.moduDrive.auth.domain.vo;

import java.util.Locale;

public record MemberEmail(String value) {

    /** Case- and whitespace-insensitive form, so "River@x.com " and "river@x.com" share one attempt
     * count and one set of known devices. */
    public String normalized() {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
