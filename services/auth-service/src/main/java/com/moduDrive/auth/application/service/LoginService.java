package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.in.usecase.LoginUseCase;
import com.moduDrive.auth.application.port.out.AuthenticateMemberPort;
import com.moduDrive.auth.application.port.out.CreateSessionPort;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;
import com.moduDrive.common.core.annotation.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.val;

@UseCase
@RequiredArgsConstructor
class LoginService implements LoginUseCase {

    private final AuthenticateMemberPort authenticateMemberPort;
    private final CreateSessionPort createSessionPort;
    private final DeleteSessionPort deleteSessionPort;

    @Override
    public SessionId login(LoginCommand loginCommand) {
        val request = new AuthenticateMemberRequest(
                loginCommand.getMemberEmail().value(),
                loginCommand.getMemberPassword().value()
        );
        MemberAuthData memberAuthData = authenticateMemberPort.authenticateMember(request);

        // Always a brand-new id (no session fixation), and the one this browser held before is
        // dropped rather than left alive in Redis.
        if (loginCommand.getPreviousSessionId() != null) {
            deleteSessionPort.deleteSession(loginCommand.getPreviousSessionId());
        }
        return createSessionPort.createSession(memberAuthData);
    }

}
