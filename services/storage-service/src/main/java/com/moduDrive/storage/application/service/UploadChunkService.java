package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.UploadChunkCommand;
import com.moduDrive.storage.application.port.in.usecase.UploadChunkUseCase;
import com.moduDrive.storage.application.port.out.FindUploadSessionPort;
import com.moduDrive.storage.domain.model.UploadSession;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.springframework.beans.factory.annotation.Value;

@UseCase
class UploadChunkService implements UploadChunkUseCase {

    private final FindUploadSessionPort findUploadSessionPort;
    private final long maxFileSizeBytes;

    UploadChunkService(FindUploadSessionPort findUploadSessionPort,
                        @Value("${modudrive.storage.max-file-size-bytes}") long maxFileSizeBytes) {
        this.findUploadSessionPort = findUploadSessionPort;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public void uploadChunk(UploadChunkCommand command) {
        UploadSession session = findUploadSessionPort.findSession(command.getSessionId())
                .orElseThrow(() -> new BusinessException(StorageExceptionCase.SESSION_NOT_FOUND));

        if (!session.getOwnerId().equals(command.getUserId())) {
            throw new BusinessException(StorageExceptionCase.SESSION_OWNER_MISMATCH);
        }
        if (session.isCompleted()) {
            throw new BusinessException(StorageExceptionCase.SESSION_ALREADY_COMPLETED);
        }
        if (command.getChunkIndex() >= session.getTotalChunks()) {
            throw new BusinessException(StorageExceptionCase.INVALID_CHUNK_INDEX);
        }

        byte[] existing = session.getChunks().get(command.getChunkIndex());
        long existingSize = existing == null ? 0 : existing.length;
        // Reject before storing the chunk, so a client can't accumulate unbounded bytes
        // in the session's heap-backed chunk map by never calling /complete (#229).
        if (session.getTotalBytes() - existingSize + command.getData().length > maxFileSizeBytes) {
            throw new BusinessException(StorageExceptionCase.FILE_TOO_LARGE);
        }

        session.addChunk(command.getChunkIndex(), command.getData());
    }
}
