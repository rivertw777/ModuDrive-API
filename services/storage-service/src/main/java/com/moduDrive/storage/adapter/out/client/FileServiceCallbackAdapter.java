package com.moduDrive.storage.adapter.out.client;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.storage.application.port.out.FileUploadCallbackPort;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@PersistenceAdapter
@RequiredArgsConstructor
class FileServiceCallbackAdapter implements FileUploadCallbackPort {

    private final FileServiceFeignClient fileServiceFeignClient;

    @Override
    public void notifyUploadComplete(UUID fileId, long fileSize, int blockCount, String s3Path) {
        fileServiceFeignClient.updateFileStatus(
                fileId.toString(),
                new FileUploadCallbackRequest(fileSize, blockCount, s3Path)
        );
    }
}
