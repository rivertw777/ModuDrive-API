package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.in.usecase.LogoutUseCase;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@WebAdapter
@RestController
class LogoutController {

    private final LogoutUseCase logoutUseCase;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @PostMapping("/api/v1/auth/logout")
    public ApiResponse<Void> logout(
            @CookieValue(value = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpServletResponse) {
        val command = new LogoutCommand(RefreshTokenCookieFactory.readRefreshToken(refreshToken));
        logoutUseCase.logout(command);
        refreshTokenCookieFactory.clearRefreshToken(httpServletResponse);

        return ApiResponse.success();
    }

}
