package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.adapter.in.web.dto.ConfirmEmailVerificationRequest;
import com.moduDrive.member.application.port.in.command.ConfirmEmailVerificationCommand;
import com.moduDrive.member.application.port.in.usecase.ConfirmEmailVerificationUseCase;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ConfirmEmailVerificationController {

    private final ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;

    @PostMapping("/api/v1/member/verify-email/confirm")
    public ApiResponse<Void> confirmEmailVerification(@Valid @RequestBody ConfirmEmailVerificationRequest request) {
        val command = new ConfirmEmailVerificationCommand(new MemberEmail(request.email()), request.code());
        confirmEmailVerificationUseCase.confirmEmailVerification(command);

        return ApiResponse.success();
    }
}
