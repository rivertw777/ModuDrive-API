package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.dto.LoginResponse;
import com.moduDrive.auth.adapter.in.web.mapper.AuthResponseMapper;
import com.moduDrive.auth.application.port.in.command.ReissueTokenCommand;
import com.moduDrive.auth.application.port.in.usecase.ReissueTokenUseCase;
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
class ReissueTokenController {

    private final ReissueTokenUseCase reissueTokenUseCase;
    private final AuthResponseMapper authResponseMapper;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @PostMapping("/api/v1/auth/reissue")
    public ApiResponse<LoginResponse> reissue(
            @CookieValue(value = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpServletResponse) {
        val command = new ReissueTokenCommand(RefreshTokenCookieFactory.readRefreshToken(refreshToken));
        val tokenPair = reissueTokenUseCase.reissueToken(command);
        refreshTokenCookieFactory.setRefreshToken(httpServletResponse, tokenPair.getRefreshToken());

        val response = authResponseMapper.toLoginResponse(tokenPair);
        return ApiResponse.success(response);
    }

}
