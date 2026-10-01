package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.application.port.in.usecase.SendLoginCodeUseCase;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mails the code of a login from a new device, on the member's request (spec 004 2-2). */
@RequiredArgsConstructor
@WebAdapter
@RestController
class SendLoginCodeController {

    private final SendLoginCodeUseCase sendLoginCodeUseCase;
    private final SessionCookieFactory sessionCookieFactory;

    @PostMapping("/api/v1/auth/verify-email/request")
    public ApiResponse<Void> sendLoginCode(HttpServletRequest httpServletRequest,
                                           HttpServletResponse httpServletResponse) {
        val challengeId = sessionCookieFactory.readLoginChallengeId(httpServletRequest)
                .orElseThrow(() -> new BusinessException(AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED));
        sendLoginCodeUseCase.sendLoginCode(challengeId);
        // The challenge's 5 minutes started over with the new code; the cookie follows.
        sessionCookieFactory.setLoginChallengeId(httpServletResponse, challengeId);
        return ApiResponse.success();
    }

}
