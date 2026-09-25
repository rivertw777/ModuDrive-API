package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.mapper.AuthResponseMapper;
import com.moduDrive.auth.application.port.in.command.ValidateSessionCommand;
import com.moduDrive.auth.application.port.in.usecase.ValidateSessionUseCase;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Gateway-only (X-Internal-Token, see InternalTokenFilter): runs in front of every request. */
@RequiredArgsConstructor
@WebAdapter
@RestController
class ValidateSessionController {

    private final ValidateSessionUseCase validateSessionUseCase;
    private final AuthResponseMapper authResponseMapper;

    @PostMapping("/internal/v1/auth/sessions/validate")
    public ApiResponse<ValidateSessionResponse> validateSession(@Valid @RequestBody ValidateSessionRequest request) {
        val command = new ValidateSessionCommand(new SessionId(request.sessionId()), request.touch());
        val memberAuthData = validateSessionUseCase.validateSession(command);

        return ApiResponse.success(authResponseMapper.toValidateSessionResponse(memberAuthData));
    }

}
