package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.InitResumableUploadCommand;
import com.moduDrive.storage.application.port.in.usecase.InitResumableUploadUseCase;
import com.moduDrive.storage.application.port.out.CreateUploadSessionPort;
import com.moduDrive.storage.domain.model.UploadSession;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class InitResumableUploadService implements InitResumableUploadUseCase {

    private final CreateUploadSessionPort createUploadSessionPort;

    @Override
    public UUID initResumableUpload(InitResumableUploadCommand command) {
        UploadSession session = UploadSession.create(command.getFileId(), command.getUserId(), command.getTotalChunks());
        createUploadSessionPort.createSession(session);
        return session.getSessionId();
    }
}
