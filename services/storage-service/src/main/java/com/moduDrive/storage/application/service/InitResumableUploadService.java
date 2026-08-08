package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.InitResumableUploadCommand;
import com.moduDrive.storage.application.port.in.usecase.InitResumableUploadUseCase;
import com.moduDrive.storage.application.port.out.CreateUploadSessionPort;
import com.moduDrive.storage.domain.model.UploadSession;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

@UseCase
class InitResumableUploadService implements InitResumableUploadUseCase {

    private final CreateUploadSessionPort createUploadSessionPort;
    private final long maxFileSizeBytes;

    InitResumableUploadService(CreateUploadSessionPort createUploadSessionPort,
                               @Value("${modudrive.storage.max-file-size-bytes}") long maxFileSizeBytes) {
        this.createUploadSessionPort = createUploadSessionPort;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public UUID initResumableUpload(InitResumableUploadCommand command) {
        if (command.getFileSize() > maxFileSizeBytes) {
            throw new BusinessException(StorageExceptionCase.FILE_TOO_LARGE);
        }
        UploadSession session = UploadSession.create(command.getFileId(), command.getUserId(), command.getTotalChunks());
        createUploadSessionPort.createSession(session);
        return session.getSessionId();
    }
}
