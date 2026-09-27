package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.api.dto.member.SendLoginVerificationMailRequest;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.application.port.in.command.SendLoginVerificationMailCommand;
import com.moduDrive.member.application.port.in.usecase.SendLoginVerificationMailUseCase;
import com.moduDrive.member.domain.model.Member.MemberId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** auth-service only: a login from a new device needs its code mailed (auth spec 004 2-1). */
@WebAdapter
@RestController
@RequiredArgsConstructor
class SendLoginVerificationMailController {

    private final SendLoginVerificationMailUseCase sendLoginVerificationMailUseCase;

    @PostMapping("/internal/v1/member/login-verification-mail")
    public ApiResponse<Void> sendLoginVerificationMail(@Valid @RequestBody SendLoginVerificationMailRequest request) {
        sendLoginVerificationMailUseCase.sendLoginVerificationMail(
                new SendLoginVerificationMailCommand(new MemberId(request.memberId()), request.code()));
        return ApiResponse.success();
    }
}
