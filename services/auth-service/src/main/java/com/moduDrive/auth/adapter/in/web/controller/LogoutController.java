package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.in.usecase.LogoutUseCase;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@WebAdapter
@RestController
class LogoutController {

    private final LogoutUseCase logoutUseCase;
    private final SessionCookieFactory sessionCookieFactory;

    // Never fails: a missing or already-expired session still ends with the cookie cleared.
    @PostMapping("/api/v1/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpServletRequest,
                                    HttpServletResponse httpServletResponse) {
        sessionCookieFactory.readSessionId(httpServletRequest)
                .ifPresent(sessionId -> logoutUseCase.logout(new LogoutCommand(sessionId)));
        sessionCookieFactory.clearSessionId(httpServletResponse);

        return ApiResponse.success();
    }

}
