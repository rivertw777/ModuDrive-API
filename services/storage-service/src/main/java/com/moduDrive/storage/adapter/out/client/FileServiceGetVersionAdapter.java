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

    // Deliberately a different route than the one below — see GetAllFileVersionsController's
    // javadoc: that route checks ownership, not the DOWNLOAD permission a VIEWER share also
    // holds, since this result feeds a permanent purge.
    @Override
    public List<VersionLocation> getAllVersions(UUID fileId, UUID userId) {
        List<FileVersionDto> versions = feignClient
                .getAllFileVersions(fileId.toString(), userId.toString())
                .getData();
        if (versions == null) {
            return List.of();
        }
        return versions.stream()
                .map(v -> new VersionLocation(v.s3Path(), v.blockCount()))
                .toList();
    }

    @Override
    public VersionLocation getLatestVersion(UUID fileId, UUID userId, boolean markAccessed) {
        FileVersionDto v = firstOrThrow(
                feignClient.getFileRevisions(fileId.toString(), userId.toString(), 1, markAccessed).getData());
        return new VersionLocation(v.s3Path(), v.blockCount());
    }

    @Override
    public VersionLocation getPublicVersion(String fileId, String key) {
        FileVersionDto v = firstOrThrow(feignClient.getPublicFileRevisions(fileId, key, 1).getData());
        return new VersionLocation(v.s3Path(), v.blockCount());
    }

    private FileVersionDto firstOrThrow(List<FileVersionDto> versions) {
        if (versions == null || versions.isEmpty()) {
            throw new BusinessException(StorageExceptionCase.FILE_NOT_FOUND_IN_STORAGE);
        }
        return versions.get(0);
    }
}
