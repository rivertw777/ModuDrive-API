package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.api.dto.member.MemberResponse;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.adapter.in.web.mapper.MemberResponseMapper;
import com.moduDrive.member.application.port.in.command.FindMemberByEmailCommand;
import com.moduDrive.member.application.port.in.usecase.FindMemberByEmailUseCase;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
class FindMemberByEmailController {

    private final FindMemberByEmailUseCase findMemberByEmailUseCase;
    private final MemberResponseMapper memberResponseMapper;

    /** A missing {@code email} is a 400. A malformed or unknown one is MEMBER_NOT_FOUND, also a 400,
     * so there's no separate format validation. */
    @GetMapping("/api/v1/member/find-by-email")
    public ApiResponse<MemberResponse> findMemberByEmail(@RequestParam String email) {
        val command = new FindMemberByEmailCommand(new MemberEmail(email));
        val member = findMemberByEmailUseCase.findMemberByEmail(command);

        val response = memberResponseMapper.toMemberResponse(member);
        return ApiResponse.success(response);
    }

}
