package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.in.usecase.LoginUseCase;
import com.moduDrive.auth.application.port.out.AuthenticateMemberPort;
import com.moduDrive.auth.application.port.out.CreateSessionPort;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.application.port.out.LoginAttemptPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;
import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.val;

@UseCase
@RequiredArgsConstructor
class LoginService implements LoginUseCase {

    private final AuthenticateMemberPort authenticateMemberPort;
    private final CreateSessionPort createSessionPort;
    private final DeleteSessionPort deleteSessionPort;
    private final LoginAttemptPort loginAttemptPort;

    @Override
    public SessionId login(LoginCommand loginCommand) {
        // Counted before the password is even checked; only a successful login clears the count.
        if (!loginAttemptPort.tryAttempt(loginCommand.getMemberEmail())) {
            throw new BusinessException(AuthExceptionCase.TOO_MANY_LOGIN_ATTEMPTS);
        }
        val request = new AuthenticateMemberRequest(
                loginCommand.getMemberEmail().value(),
                loginCommand.getMemberPassword().value()
        );
        MemberAuthData memberAuthData = authenticateMemberPort.authenticateMember(request);
        loginAttemptPort.clearAttempts(loginCommand.getMemberEmail());

        // Always a brand-new id (no session fixation), and the one this browser held before is
        // dropped rather than left alive in Redis.
        if (loginCommand.getPreviousSessionId() != null) {
            deleteSessionPort.deleteSession(loginCommand.getPreviousSessionId());
        }
        return createSessionPort.createSession(memberAuthData);
    }

}
