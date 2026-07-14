package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.SimpleUploadCommand;
import com.moduDrive.storage.application.port.in.usecase.SimpleUploadUseCase;
import com.moduDrive.storage.application.port.out.FileUploadCallbackPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class SimpleUploadService implements SimpleUploadUseCase {

    private static final int DEFAULT_BLOCK_SIZE = 4 * 1024 * 1024;

    private final StoreBlocksPort storeBlocksPort;
    private final FileUploadCallbackPort callbackPort;

    @Override
    public void simpleUpload(SimpleUploadCommand command) {
        String s3Path = "files/" + command.getFileId() + "/" + UUID.randomUUID();
        List<byte[]> blocks = splitIntoBlocks(command.getData(), DEFAULT_BLOCK_SIZE);
        int blockCount = storeBlocksPort.storeBlocks(s3Path, blocks);
        callbackPort.notifyUploadComplete(command.getFileId(), command.getFileSize(), blockCount, s3Path);
    }

    private List<byte[]> splitIntoBlocks(byte[] data, int size) {
        List<byte[]> blocks = new ArrayList<>();
        if (data.length == 0) {
            blocks.add(new byte[0]);
            return blocks;
        }
        for (int offset = 0; offset < data.length; offset += size) {
            blocks.add(Arrays.copyOfRange(data, offset, Math.min(offset + size, data.length)));
        }
        return blocks;
    }
}
