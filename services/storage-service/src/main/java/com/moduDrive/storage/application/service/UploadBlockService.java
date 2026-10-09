package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.UploadBlockCommand;
import com.moduDrive.storage.application.port.in.usecase.UploadBlockUseCase;
import com.moduDrive.storage.application.port.out.RecordUploadedBlockPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
import com.moduDrive.storage.domain.model.Blocks;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.springframework.beans.factory.annotation.Value;

/** Spec 008: one block, stored in the caller's own space under its hash. The hash is recomputed from
 * the bytes — trusting the client's would let it put any bytes under any hash, and every file that
 * shares that hash would then read them. Memory per request is one block. */
@UseCase
class UploadBlockService implements UploadBlockUseCase {

    private final StoreBlocksPort storeBlocksPort;
    private final RecordUploadedBlockPort recordUploadedBlockPort;
    private final int blockSize;
    private final int uploadLimit;

    UploadBlockService(StoreBlocksPort storeBlocksPort,
                       RecordUploadedBlockPort recordUploadedBlockPort,
                       @Value("${storage.block-size}") int blockSize,
                       @Value("${storage.upload-blocks-per-window}") int uploadLimit) {
        this.storeBlocksPort = storeBlocksPort;
        this.recordUploadedBlockPort = recordUploadedBlockPort;
        this.blockSize = blockSize;
        this.uploadLimit = uploadLimit;
    }

    @Override
    public void uploadBlock(UploadBlockCommand command) {
        byte[] data = command.getData();
        if (data.length > blockSize) {
            throw new BusinessException(StorageExceptionCase.BLOCK_TOO_LARGE);
        }
        // An empty file has no blocks, so an empty block is never needed.
        if (data.length == 0 || !Blocks.sha256Hex(data).equals(command.getHash())) {
            throw new BusinessException(StorageExceptionCase.INVALID_BLOCK);
        }
        if (!recordUploadedBlockPort.tryCountUpload(command.getUserId(), uploadLimit)) {
            throw new BusinessException(StorageExceptionCase.UPLOAD_LIMIT_EXCEEDED);
        }
        storeBlocksPort.storeBlock(Blocks.key(command.getUserId(), command.getHash()), data);
        recordUploadedBlockPort.recordUploaded(command.getUserId(), command.getHash(), data.length);
    }
}
