package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.application.port.in.command.FindMemberByEmailCommand;
import com.moduDrive.member.application.port.in.usecase.FindMemberByEmailUseCase;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
class FindMemberByEmailController {

    private final FindMemberByEmailUseCase findMemberByEmailUseCase;

    /** Only says whether the email is a member (200, no data) — the share dialog needs nothing more,
     * and any signed-in user can call it. A missing {@code email} is a 400; a malformed or unknown one
     * is MEMBER_NOT_FOUND, also a 400. */
    @GetMapping("/api/v1/member/find-by-email")
    public ApiResponse<Void> findMemberByEmail(@RequestParam String email) {
        findMemberByEmailUseCase.findMemberByEmail(new FindMemberByEmailCommand(new MemberEmail(email)));
        return ApiResponse.success();
    }

}
