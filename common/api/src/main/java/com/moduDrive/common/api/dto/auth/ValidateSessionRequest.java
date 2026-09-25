package com.moduDrive.common.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * gateway → auth-service session check. The id travels in the body, never the URL, so it stays
 * out of access logs. {@code touch=false} checks without restarting the idle timeout.
 */
public record ValidateSessionRequest(
        @NotBlank String sessionId,
        boolean touch
) {
    @Override
    public String toString() {
        return "ValidateSessionRequest[sessionId=***, touch=" + touch + "]";
    }
}
