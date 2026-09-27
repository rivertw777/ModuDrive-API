package com.moduDrive.auth.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyLoginRequest(
        @NotBlank(message = "인증 코드는 필수입니다")
        String code
) {
}
