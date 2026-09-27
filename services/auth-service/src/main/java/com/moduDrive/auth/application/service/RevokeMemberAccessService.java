package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.RevokeMemberAccessCommand;
import com.moduDrive.auth.application.port.in.usecase.RevokeMemberAccessUseCase;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.application.port.out.KnownDevicePort;
import com.moduDrive.common.core.annotation.UseCase;
import lombok.RequiredArgsConstructor;

/**
 * After a password change: every session ends and every verified device is forgotten (spec 004 5).
 * Otherwise a device someone verified with the leaked old password would sign straight in again
 * the moment they learn the new one, with no emailed code.
 */
@UseCase
@RequiredArgsConstructor
class RevokeMemberAccessService implements RevokeMemberAccessUseCase {

    private final KnownDevicePort knownDevicePort;
    private final DeleteSessionPort deleteSessionPort;

    @Override
    public void revokeMemberAccess(RevokeMemberAccessCommand command) {
        knownDevicePort.forgetAll(command.getMemberId());
        deleteSessionPort.deleteAllSessions(command.getMemberId());
    }
}
