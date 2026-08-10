package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.dto.LogoutRequest;
import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.in.usecase.LogoutUseCase;
import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
import com.moduDrive.auth.domain.model.TokenPair.RefreshToken;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@WebAdapter
@RestController
class LogoutController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final LogoutUseCase logoutUseCase;

    @PostMapping("/api/v1/auth/logout")
    public ApiResponse<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        val command = new LogoutCommand(
                new RefreshToken(request.refreshToken()),
                extractAccessToken(authorizationHeader)
        );
        logoutUseCase.logout(command);

        return ApiResponse.success();
    }

    private static AccessToken extractAccessToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return new AccessToken(authorizationHeader.substring(BEARER_PREFIX.length()));
    }

}
