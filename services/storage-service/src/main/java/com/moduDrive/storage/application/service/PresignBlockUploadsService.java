package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.PresignBlockUploadsCommand;
import com.moduDrive.storage.application.port.in.command.PresignBlockUploadsCommand.Block;
import com.moduDrive.storage.application.port.in.usecase.PresignBlockUploadsUseCase;
import com.moduDrive.storage.application.port.out.PresignBlockUploadPort;
import com.moduDrive.storage.application.port.out.RecordUploadedBlockPort;
import com.moduDrive.storage.domain.model.Blocks;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

/** Spec 001 6: the block bytes go from the WEB straight to S3; this only signs where they may land —
 * always the caller's own space, so a hash alone never reaches another owner's block. Every block
 * is checked before any URL is handed out. */
@UseCase
class PresignBlockUploadsService implements PresignBlockUploadsUseCase {

    /** One request's cap: a commit's needBlocks go out in requests of this many. */
    static final int MAX_BLOCKS_PER_REQUEST = 64;

    private final PresignBlockUploadPort presignBlockUploadPort;
    private final RecordUploadedBlockPort recordUploadedBlockPort;
    private final int blockSize;
    private final int uploadLimit;

    PresignBlockUploadsService(PresignBlockUploadPort presignBlockUploadPort,
                               RecordUploadedBlockPort recordUploadedBlockPort,
                               @Value("${storage.block-size}") int blockSize,
                               @Value("${storage.upload-blocks-per-window}") int uploadLimit) {
        this.presignBlockUploadPort = presignBlockUploadPort;
        this.recordUploadedBlockPort = recordUploadedBlockPort;
        this.blockSize = blockSize;
        this.uploadLimit = uploadLimit;
    }

    @Override
    public List<String> presignBlockUploads(PresignBlockUploadsCommand command) {
        List<Block> blocks = command.getBlocks();
        if (blocks.isEmpty() || blocks.size() > MAX_BLOCKS_PER_REQUEST) {
            throw new BusinessException(StorageExceptionCase.INVALID_BLOCK);
        }
        for (Block block : blocks) {
            // An empty file has no blocks, so an empty block is never needed.
            if (!Blocks.isHash(block.hash()) || block.size() < 1) {
                throw new BusinessException(StorageExceptionCase.INVALID_BLOCK);
            }
            if (block.size() > blockSize) {
                throw new BusinessException(StorageExceptionCase.BLOCK_TOO_LARGE);
            }
        }
        List<String> urls = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            if (!recordUploadedBlockPort.tryCountUpload(command.getUserId(), uploadLimit)) {
                throw new BusinessException(StorageExceptionCase.UPLOAD_LIMIT_EXCEEDED);
            }
            // Schedule → sign (spec 001 6-1): see RecordUploadedBlockPort for why.
            recordUploadedBlockPort.scheduleSweep(command.getUserId(), block.hash());
            urls.add(presignBlockUploadPort.presignUpload(
                    Blocks.key(command.getUserId(), block.hash()), block.size(), block.hash()));
        }
        return urls;
    }
}
