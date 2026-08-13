package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.RevokeFileShareCommand;

public interface RevokeFileShareUseCase {

    void revokeFileShare(RevokeFileShareCommand command);
}
