package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.adapter.in.web.dto.RequestEmailVerificationRequest;
import com.moduDrive.member.application.port.in.command.RequestEmailVerificationCommand;
import com.moduDrive.member.application.port.in.usecase.RequestEmailVerificationUseCase;
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
class RequestEmailVerificationController {

    private final RequestEmailVerificationUseCase requestEmailVerificationUseCase;

    @PostMapping("/api/v1/member/verify-email/request")
    public ApiResponse<Void> requestEmailVerification(@Valid @RequestBody RequestEmailVerificationRequest request) {
        val command = new RequestEmailVerificationCommand(new MemberEmail(request.email()));
        requestEmailVerificationUseCase.requestEmailVerification(command);

        return ApiResponse.success();
    }

}
