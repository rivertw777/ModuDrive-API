package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.adapter.in.web.dto.ChangePasswordRequest;
import com.moduDrive.member.application.port.in.command.ChangePasswordCommand;
import com.moduDrive.member.application.port.in.usecase.ChangePasswordUseCase;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ChangePasswordController {

    private final ChangePasswordUseCase changePasswordUseCase;

    @PatchMapping("/api/v1/member/password")
    public ApiResponse<Void> changePassword(@RequestHeader(name = "X_USER_ID") String memberId,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        val command = new ChangePasswordCommand(
                new MemberId(UUID.fromString(memberId)),
                new MemberPassword(request.currentPassword()),
                new MemberPassword(request.newPassword())
        );
        changePasswordUseCase.changePassword(command);
        return ApiResponse.success();
    }

}
