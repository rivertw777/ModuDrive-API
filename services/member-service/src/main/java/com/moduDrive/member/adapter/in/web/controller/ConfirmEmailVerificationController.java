package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.application.port.in.command.ConfirmEmailVerificationCommand;
import com.moduDrive.member.application.port.in.usecase.ConfirmEmailVerificationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ConfirmEmailVerificationController {

    private final ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;

    @GetMapping("/api/v1/member/verify-email")
    public ApiResponse<Void> confirmEmailVerification(@RequestParam String token) {
        confirmEmailVerificationUseCase.confirmEmailVerification(new ConfirmEmailVerificationCommand(token));
        return ApiResponse.success();
    }
}
