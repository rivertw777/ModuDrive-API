package com.moduDrive.storage.adapter.out.client;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.exception.StorageExceptionCase;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@PersistenceAdapter
@RequiredArgsConstructor
class FileServiceGetVersionAdapter implements GetFileVersionPort {

    private final FileServiceFeignClient feignClient;

    @Override
    public String getS3Path(UUID fileId, UUID userId) {
        return latestVersion(fileId, userId).s3Path();
    }

    @Override
    public int getBlockCount(UUID fileId, UUID userId) {
        return latestVersion(fileId, userId).blockCount();
    }

    private FileVersionDto latestVersion(UUID fileId, UUID userId) {
        List<FileVersionDto> versions = feignClient.getFileRevisions(fileId.toString(), userId.toString(), 1).getData();
        if (versions == null || versions.isEmpty()) {
            throw new BusinessException(StorageExceptionCase.FILE_NOT_FOUND_IN_STORAGE);
        }
        return versions.get(0);
    }
}
