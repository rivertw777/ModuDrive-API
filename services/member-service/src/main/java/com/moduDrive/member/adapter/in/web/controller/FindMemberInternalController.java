package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.api.dto.member.MemberResponse;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.adapter.in.web.mapper.MemberResponseMapper;
import com.moduDrive.member.application.port.in.command.FindMemberByEmailCommand;
import com.moduDrive.member.application.port.in.command.FindMemberCommand;
import com.moduDrive.member.application.port.in.usecase.FindMemberByEmailUseCase;
import com.moduDrive.member.application.port.in.usecase.FindMemberUseCase;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.domain.model.Member.MemberId;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Member lookups for other services (file-service resolving share targets), behind the internal
 * token. Kept apart from the WEB's /api/v1/member/find — which only ever answers "who am I" off the
 * gateway-set X_USER_ID — so looking up an arbitrary member is never reachable from outside (#441). */
@WebAdapter
@RestController
@RequiredArgsConstructor
class FindMemberInternalController {

    private final FindMemberUseCase findMemberUseCase;
    private final FindMemberByEmailUseCase findMemberByEmailUseCase;
    private final MemberResponseMapper memberResponseMapper;

    @GetMapping("/internal/v1/member/{memberId}")
    public ApiResponse<MemberResponse> findMemberById(@PathVariable UUID memberId) {
        val member = findMemberUseCase.findMember(new FindMemberCommand(new MemberId(memberId)));
        return ApiResponse.success(memberResponseMapper.toMemberResponse(member));
    }

    @GetMapping("/internal/v1/member/by-email")
    public ApiResponse<MemberResponse> findMemberByEmail(@RequestParam String email) {
        val member = findMemberByEmailUseCase.findMemberByEmail(new FindMemberByEmailCommand(new MemberEmail(email)));
        return ApiResponse.success(memberResponseMapper.toMemberResponse(member));
    }
}
