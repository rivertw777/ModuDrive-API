package com.moduDrive.auth.application.port.in.usecase;

import com.moduDrive.auth.application.port.in.command.RevokeMemberAccessCommand;

public interface RevokeMemberAccessUseCase {
    void revokeMemberAccess(RevokeMemberAccessCommand revokeMemberAccessCommand);
}
