package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;
import com.moduDrive.common.api.dto.member.AuthenticateMemberResponse;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.adapter.in.web.mapper.MemberResponseMapper;
import com.moduDrive.member.application.port.in.command.AuthenticateMemberCommand;
import com.moduDrive.member.application.port.in.usecase.AuthenticateMemberUseCase;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
class AuthenticateMemberController {

    private final AuthenticateMemberUseCase authenticateMemberUseCase;
    private final MemberResponseMapper memberResponseMapper;

    @PostMapping("/internal/v1/member/authenticate")
    public ApiResponse<AuthenticateMemberResponse> authenticateMember(@Valid @RequestBody AuthenticateMemberRequest request) {
        val command = new AuthenticateMemberCommand(
                new MemberEmail(request.email()),
                new MemberPassword(request.password())
        );
        val member = authenticateMemberUseCase.authenticateMember(command);

        val response = memberResponseMapper.toAuthenticateMemberResponse(member);
        return ApiResponse.success(response);
    }

}
