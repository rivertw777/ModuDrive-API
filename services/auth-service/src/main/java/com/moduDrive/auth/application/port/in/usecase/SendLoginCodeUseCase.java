package com.moduDrive.auth.application.port.in.usecase;

import com.moduDrive.auth.domain.vo.LoginChallengeId;

public interface SendLoginCodeUseCase {
    void sendLoginCode(LoginChallengeId challengeId);
}
