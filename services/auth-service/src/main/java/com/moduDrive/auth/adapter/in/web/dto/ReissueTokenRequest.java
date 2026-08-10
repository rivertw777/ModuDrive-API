package com.moduDrive.auth.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ReissueTokenRequest(
        @NotBlank String refreshToken
) {
}
