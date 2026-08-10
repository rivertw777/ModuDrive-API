package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.api.dto.member.AuthenticateMemberResponse;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.adapter.in.web.mapper.MemberResponseMapper;
import com.moduDrive.member.application.port.in.command.FindMemberCommand;
import com.moduDrive.member.application.port.in.usecase.FindMemberUseCase;
import com.moduDrive.member.domain.model.Member.MemberId;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class GetMemberStatusController {

    private final FindMemberUseCase findMemberUseCase;
    private final MemberResponseMapper memberResponseMapper;

    @GetMapping("/api/v1/member/{memberId}/status")
    public ApiResponse<AuthenticateMemberResponse> getMemberStatus(@PathVariable String memberId) {
        val command = new FindMemberCommand(
                new MemberId(UUID.fromString(memberId))
        );
        val member = findMemberUseCase.findMember(command);

        val response = memberResponseMapper.toAuthenticateMemberResponse(member);
        return ApiResponse.success(response);
    }

}
