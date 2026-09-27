package com.moduDrive.common.api.dto.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** auth-service → member-service: mail this login code to the member's own address (auth spec 004 2-1). */
public record SendLoginVerificationMailRequest(
        @NotNull UUID memberId,
        @NotBlank String code
) {
}
