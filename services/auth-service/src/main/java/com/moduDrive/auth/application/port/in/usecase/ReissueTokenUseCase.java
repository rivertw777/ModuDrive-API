package com.moduDrive.auth.application.port.in.usecase;

import com.moduDrive.auth.application.port.in.command.ReissueTokenCommand;
import com.moduDrive.auth.domain.model.TokenPair;

public interface ReissueTokenUseCase {
    TokenPair reissueToken(ReissueTokenCommand reissueTokenCommand);
}
