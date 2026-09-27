package com.moduDrive.auth.application.port.in.usecase;

import com.moduDrive.auth.application.port.in.command.VerifyLoginCommand;
import com.moduDrive.auth.domain.model.LoginResult;

public interface VerifyLoginUseCase {
    LoginResult.SignedIn verifyLogin(VerifyLoginCommand verifyLoginCommand);
}
