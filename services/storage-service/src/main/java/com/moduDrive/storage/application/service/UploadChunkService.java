package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.UploadChunkCommand;
import com.moduDrive.storage.application.port.in.usecase.UploadChunkUseCase;
import com.moduDrive.storage.application.port.out.FindUploadSessionPort;
import com.moduDrive.storage.domain.model.UploadSession;
import com.moduDrive.storage.exception.StorageExceptionCase;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class UploadChunkService implements UploadChunkUseCase {

    private final FindUploadSessionPort findUploadSessionPort;

    @Override
    public void uploadChunk(UploadChunkCommand command) {
        UploadSession session = findUploadSessionPort.findSession(command.getSessionId())
                .orElseThrow(() -> new BusinessException(StorageExceptionCase.SESSION_NOT_FOUND));

        if (session.getOwnerId() != command.getUserId()) {
            throw new BusinessException(StorageExceptionCase.SESSION_OWNER_MISMATCH);
        }
        if (session.isCompleted()) {
            throw new BusinessException(StorageExceptionCase.SESSION_ALREADY_COMPLETED);
        }

        session.addChunk(command.getChunkIndex(), command.getData());
    }
}
