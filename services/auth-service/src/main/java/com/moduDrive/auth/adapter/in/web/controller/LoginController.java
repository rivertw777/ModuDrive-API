package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.dto.LoginRequest;
import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.in.usecase.LoginUseCase;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.auth.domain.vo.MemberPassword;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@WebAdapter
@RestController
class LoginController {

    private final LoginUseCase loginUseCase;
    private final SessionCookieFactory sessionCookieFactory;

    // The response body carries no credential — the session id only ever travels in the cookie.
    @PostMapping("/api/v1/auth/login")
    public ApiResponse<Void> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpServletRequest,
                                   HttpServletResponse httpServletResponse) {
        val command = new LoginCommand(
                new MemberEmail(request.email()),
                new MemberPassword(request.password()),
                sessionCookieFactory.readSessionId(httpServletRequest).orElse(null)
        );
        val sessionId = loginUseCase.login(command);
        sessionCookieFactory.setSessionId(httpServletResponse, sessionId);

        return ApiResponse.success();
    }

}
