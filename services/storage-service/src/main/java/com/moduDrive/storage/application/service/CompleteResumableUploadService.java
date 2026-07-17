package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.CompleteResumableUploadCommand;
import com.moduDrive.storage.application.port.in.usecase.CompleteResumableUploadUseCase;
import com.moduDrive.storage.application.port.out.FileUploadCallbackPort;
import com.moduDrive.storage.application.port.out.FindUploadSessionPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
import com.moduDrive.storage.domain.model.UploadSession;
import com.moduDrive.storage.exception.StorageExceptionCase;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@UseCase
@RequiredArgsConstructor
class CompleteResumableUploadService implements CompleteResumableUploadUseCase {

    private final FindUploadSessionPort findUploadSessionPort;
    private final StoreBlocksPort storeBlocksPort;
    private final FileUploadCallbackPort callbackPort;

    @Override
    public void completeResumableUpload(CompleteResumableUploadCommand command) {
        UploadSession session = findUploadSessionPort.findSession(command.getSessionId())
                .orElseThrow(() -> new BusinessException(StorageExceptionCase.SESSION_NOT_FOUND));

        if (session.getOwnerId() != command.getUserId()) {
            throw new BusinessException(StorageExceptionCase.SESSION_OWNER_MISMATCH);
        }
        if (session.isCompleted()) {
            throw new BusinessException(StorageExceptionCase.SESSION_ALREADY_COMPLETED);
        }
        if (!session.isAllChunksReceived()) {
            throw new BusinessException(StorageExceptionCase.CHUNKS_INCOMPLETE);
        }

        List<byte[]> orderedChunks = IntStream.range(0, session.getTotalChunks())
                .mapToObj(i -> session.getChunks().get(i))
                .toList();

        long totalSize = orderedChunks.stream().mapToLong(b -> b.length).sum();
        String s3Path = "files/" + session.getFileId() + "/" + UUID.randomUUID();
        int blockCount = storeBlocksPort.storeBlocks(s3Path, orderedChunks);

        callbackPort.notifyUploadComplete(session.getFileId(), totalSize, blockCount, s3Path);
        session.markCompleted();
    }
}
