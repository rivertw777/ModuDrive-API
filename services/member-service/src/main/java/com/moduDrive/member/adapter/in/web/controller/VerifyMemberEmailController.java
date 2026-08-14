package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.application.port.in.command.VerifyMemberEmailCommand;
import com.moduDrive.member.application.port.in.usecase.VerifyMemberEmailUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
class VerifyMemberEmailController {

    private final VerifyMemberEmailUseCase verifyMemberEmailUseCase;

    @GetMapping("/api/v1/member/verify-email")
    public ApiResponse<Void> verifyEmail(@RequestParam String token) {
        verifyMemberEmailUseCase.verifyMemberEmail(new VerifyMemberEmailCommand(token));
        return ApiResponse.success();
    }
}
