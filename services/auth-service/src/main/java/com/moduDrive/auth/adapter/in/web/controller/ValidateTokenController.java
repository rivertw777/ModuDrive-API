package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.mapper.AuthResponseMapper;
import com.moduDrive.auth.application.port.in.command.ValidateTokenCommand;
import com.moduDrive.auth.application.port.in.usecase.ValidateTokenUseCase;
import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
import com.moduDrive.common.api.dto.auth.ValidateTokenRequest;
import com.moduDrive.common.api.dto.auth.ValidateTokenResponse;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@WebAdapter
@RestController
class ValidateTokenController {

    private final ValidateTokenUseCase validateTokenUseCase;
    private final AuthResponseMapper authResponseMapper;

    @PostMapping("/api/v1/auth/validate-token")
    public ApiResponse<ValidateTokenResponse> validateToken(@Valid @RequestBody ValidateTokenRequest request) {
        val command = new ValidateTokenCommand(
                new AccessToken(request.token())
        );
        val memberAuthData = validateTokenUseCase.validateToken(command);

        val response = authResponseMapper.toValidateTokenResponse(memberAuthData);
        return ApiResponse.success(response);
    }

}
