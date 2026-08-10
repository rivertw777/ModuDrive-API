package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.dto.LoginResponse;
import com.moduDrive.auth.adapter.in.web.dto.ReissueTokenRequest;
import com.moduDrive.auth.adapter.in.web.mapper.AuthResponseMapper;
import com.moduDrive.auth.application.port.in.command.ReissueTokenCommand;
import com.moduDrive.auth.application.port.in.usecase.ReissueTokenUseCase;
import com.moduDrive.auth.domain.model.TokenPair.RefreshToken;
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
class ReissueTokenController {

    private final ReissueTokenUseCase reissueTokenUseCase;
    private final AuthResponseMapper authResponseMapper;

    @PostMapping("/api/v1/auth/reissue")
    public ApiResponse<LoginResponse> reissue(@Valid @RequestBody ReissueTokenRequest request) {
        val command = new ReissueTokenCommand(new RefreshToken(request.refreshToken()));
        val tokenPair = reissueTokenUseCase.reissueToken(command);

        val response = authResponseMapper.toLoginResponse(tokenPair);
        return ApiResponse.success(response);
    }

}
