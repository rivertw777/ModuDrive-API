package com.moduDrive.auth.domain.vo;

/** The raw session identifier the browser holds in its cookie — a bearer credential. */
public record SessionId(String value) {

    // Keep the credential out of any log line or exception message that stringifies this record.
    @Override
    public String toString() {
        return "SessionId[***]";
    }
}
