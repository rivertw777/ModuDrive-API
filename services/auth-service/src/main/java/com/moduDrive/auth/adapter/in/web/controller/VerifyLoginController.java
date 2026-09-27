package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.dto.VerifyLoginRequest;
import com.moduDrive.auth.application.port.in.command.VerifyLoginCommand;
import com.moduDrive.auth.application.port.in.usecase.VerifyLoginUseCase;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The emailed-code step of a login from a new device (spec 004 2-1). */
@RequiredArgsConstructor
@WebAdapter
@RestController
class VerifyLoginController {

    private final VerifyLoginUseCase verifyLoginUseCase;
    private final SessionCookieFactory sessionCookieFactory;

    @PostMapping("/api/v1/auth/login/verify")
    public ApiResponse<Void> verifyLogin(@Valid @RequestBody VerifyLoginRequest request,
                                         HttpServletRequest httpServletRequest,
                                         HttpServletResponse httpServletResponse) {
        // No challenge cookie: it was never issued or its 10 minutes are up — same answer either way.
        val challengeId = sessionCookieFactory.readLoginChallengeId(httpServletRequest)
                .orElseThrow(() -> new BusinessException(AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED));
        val command = new VerifyLoginCommand(
                challengeId,
                request.code(),
                sessionCookieFactory.readDeviceId(httpServletRequest).orElse(null),
                sessionCookieFactory.readSessionId(httpServletRequest).orElse(null)
        );
        val signedIn = verifyLoginUseCase.verifyLogin(command);

        sessionCookieFactory.setSessionId(httpServletResponse, signedIn.sessionId());
        sessionCookieFactory.setDeviceId(httpServletResponse, signedIn.deviceId());
        sessionCookieFactory.clearLoginChallengeId(httpServletResponse);
        return ApiResponse.success();
    }

}
