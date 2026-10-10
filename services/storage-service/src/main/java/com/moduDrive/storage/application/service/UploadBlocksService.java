package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.UploadBlocksCommand;
import com.moduDrive.storage.application.port.in.command.UploadBlocksCommand.Block;
import com.moduDrive.storage.application.port.in.usecase.UploadBlocksUseCase;
import com.moduDrive.storage.application.port.out.RecordUploadedBlockPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
import com.moduDrive.storage.domain.model.Blocks;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/** Spec 001 2장 4번: several blocks per request, each stored in the caller's own space under its
 * hash. The hash is recomputed from the bytes — trusting the client's would let it put any bytes
 * under any hash, and every file that shares that hash would then read them. Every block is checked
 * before any is stored, so a bad one leaves nothing behind. */
@UseCase
class UploadBlocksService implements UploadBlocksUseCase {

    /** One request's cap: memory per request, and how much one retry resends. */
    static final int MAX_BLOCKS_PER_REQUEST = 64;
    static final long MAX_BYTES_PER_REQUEST = 8L * 1024 * 1024;

    private final StoreBlocksPort storeBlocksPort;
    private final RecordUploadedBlockPort recordUploadedBlockPort;
    private final int blockSize;
    private final int uploadLimit;

    UploadBlocksService(StoreBlocksPort storeBlocksPort,
                        RecordUploadedBlockPort recordUploadedBlockPort,
                        @Value("${storage.block-size}") int blockSize,
                        @Value("${storage.upload-blocks-per-window}") int uploadLimit) {
        this.storeBlocksPort = storeBlocksPort;
        this.recordUploadedBlockPort = recordUploadedBlockPort;
        this.blockSize = blockSize;
        this.uploadLimit = uploadLimit;
    }

    @Override
    public void uploadBlocks(UploadBlocksCommand command) {
        List<Block> blocks = command.getBlocks();
        if (blocks.isEmpty()) {
            throw new BusinessException(StorageExceptionCase.INVALID_BLOCK);
        }
        long total = blocks.stream().mapToLong(block -> block.data().length).sum();
        if (blocks.size() > MAX_BLOCKS_PER_REQUEST || total > MAX_BYTES_PER_REQUEST) {
            throw new BusinessException(StorageExceptionCase.BLOCK_BATCH_TOO_LARGE);
        }
        for (Block block : blocks) {
            if (block.data().length > blockSize) {
                throw new BusinessException(StorageExceptionCase.BLOCK_TOO_LARGE);
            }
            // An empty file has no blocks, so an empty block is never needed.
            if (block.data().length == 0 || !Blocks.sha256Hex(block.data()).equals(block.hash())) {
                throw new BusinessException(StorageExceptionCase.INVALID_BLOCK);
            }
        }
        for (Block block : blocks) {
            if (!recordUploadedBlockPort.tryCountUpload(command.getUserId(), uploadLimit)) {
                throw new BusinessException(StorageExceptionCase.UPLOAD_LIMIT_EXCEEDED);
            }
            // Schedule → store → record (spec 001 2장 4번): see RecordUploadedBlockPort for why.
            recordUploadedBlockPort.scheduleSweep(command.getUserId(), block.hash());
            storeBlocksPort.storeBlock(Blocks.key(command.getUserId(), block.hash()), block.data());
            recordUploadedBlockPort.recordUploaded(command.getUserId(), block.hash(), block.data().length);
        }
    }
}
