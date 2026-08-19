package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.ResolveViewIdentityCommand;
import com.moduDrive.storage.application.port.in.usecase.ResolveViewIdentityUseCase;
import com.moduDrive.storage.application.port.out.StreamTokenPort;
import com.moduDrive.storage.application.port.out.StreamTokenTarget;
import com.moduDrive.storage.exception.StorageExceptionCase;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class ResolveViewIdentityService implements ResolveViewIdentityUseCase {

    private final StreamTokenPort streamTokenPort;

    @Override
    public UUID resolve(ResolveViewIdentityCommand command) {
        // The header is the stronger, gateway-verified credential — checked first so it wins
        // over a streamToken that happens to also be present on the same request (e.g. a
        // logged-in tab pasting a shared preview URL).
        if (command.getHeaderUserId() != null) {
            return command.getHeaderUserId();
        }
        if (command.getStreamToken() != null) {
            return streamTokenPort.resolve(command.getStreamToken())
                    .filter(target -> target.fileId().equals(command.getFileId()))
                    .map(StreamTokenTarget::userId)
                    .orElseThrow(() -> new BusinessException(StorageExceptionCase.UNAUTHENTICATED_VIEW_REQUEST));
        }
        throw new BusinessException(StorageExceptionCase.UNAUTHENTICATED_VIEW_REQUEST);
    }
}
