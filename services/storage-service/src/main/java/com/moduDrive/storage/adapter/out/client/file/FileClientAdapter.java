package com.moduDrive.storage.adapter.out.client.file;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.out.ArchiveRequest;
import com.moduDrive.storage.application.port.out.FileUploadCallbackPort;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.exception.StorageExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class FileClientAdapter implements GetFileVersionPort, GetArchiveEntriesPort, FileUploadCallbackPort {

    private final FileClient fileClient;

    @Override
    public VersionLocation getLatestVersion(UUID fileId, UUID userId, boolean markAccessed) {
        FileVersionDto v = firstOrThrow(
                fileClient.getFileRevisions(fileId.toString(), userId.toString(), 1, markAccessed).getData());
        return new VersionLocation(v.s3Path(), v.blockCount());
    }

    @Override
    public VersionLocation getPublicVersion(String fileId, String key) {
        FileVersionDto v = firstOrThrow(fileClient.getPublicFileRevisions(fileId, key, 1).getData());
        return new VersionLocation(v.s3Path(), v.blockCount());
    }

    @Override
    public List<ArchiveEntry> getArchiveEntries(ArchiveRequest request) {
        List<ArchiveEntryDto> entries = request.isPublic()
                ? fileClient.resolvePublicArchiveEntries(
                        new ResolvePublicArchiveEntriesRequest(request.key(), request.fileIds())).getData()
                : fileClient.resolveArchiveEntries(
                        new ResolveArchiveEntriesRequest(request.userId(), request.fileIds())).getData();
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
                .map(e -> new ArchiveEntry(e.path(), e.fileId(), e.s3Path(),
                        e.blockCount() == null ? 0 : e.blockCount(),
                        e.fileSize() == null ? 0 : e.fileSize()))
                .toList();
    }

    @Override
    public void notifyUploadComplete(UUID fileId, UUID userId, long fileSize, int blockCount, String s3Path) {
        fileClient.updateFileStatus(
                fileId.toString(),
                userId.toString(),
                new FileUploadCallbackRequest(fileSize, blockCount, s3Path)
        );
    }

    private FileVersionDto firstOrThrow(List<FileVersionDto> versions) {
        if (versions == null || versions.isEmpty()) {
            throw new BusinessException(StorageExceptionCase.FILE_NOT_FOUND_IN_STORAGE);
        }
        return versions.get(0);
    }
}
