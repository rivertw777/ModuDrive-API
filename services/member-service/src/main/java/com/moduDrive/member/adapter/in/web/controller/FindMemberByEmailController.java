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

    /** {@code email} is required, so a missing one is already a 400. An email that is present but
     * malformed or unknown falls through to MEMBER_NOT_FOUND from the lookup — the same 400 answer,
     * without a second validation path to keep in sync. */
    @GetMapping("/api/v1/member/find-by-email")
    public ApiResponse<MemberResponse> findMemberByEmail(@RequestParam String email) {
        val command = new FindMemberByEmailCommand(new MemberEmail(email));
        val member = findMemberByEmailUseCase.findMemberByEmail(command);

        val response = memberResponseMapper.toMemberResponse(member);
        return ApiResponse.success(response);
    }

}
