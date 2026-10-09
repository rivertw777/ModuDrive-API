package com.moduDrive.storage.adapter.out.client.file;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.out.ArchiveRequest;
import com.moduDrive.storage.application.port.out.FindCommittedBlocksPort;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.domain.model.Blocks;
import com.moduDrive.storage.exception.StorageExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class FileClientAdapter implements GetFileVersionPort, GetArchiveEntriesPort, FindCommittedBlocksPort {

    private final FileClient fileClient;

    @Override
    public VersionLocation getLatestVersion(UUID fileId, UUID userId, boolean markAccessed) {
        return locate(firstOrThrow(
                fileClient.getFileRevisions(fileId.toString(), userId.toString(), 1, markAccessed).getData()));
    }

    @Override
    public VersionLocation getPublicVersion(String fileId, String key) {
        return locate(firstOrThrow(fileClient.getPublicFileRevisions(fileId, key, 1).getData()));
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
                .map(e -> e.hashes() == null
                        ? new ArchiveEntry(e.path(), null, null, null, 0)
                        : new ArchiveEntry(e.path(), e.fileId(), e.versionId().toString(),
                                blockKeys(e.ownerId(), e.hashes()), e.fileSize() == null ? 0 : e.fileSize()))
                .toList();
    }

    @Override
    public Set<String> findCommitted(UUID ownerId, List<String> hashes) {
        List<String> committed = fileClient.findCommittedBlocks(new FindCommittedBlocksRequest(ownerId, hashes)).getData();
        return committed == null ? Set.of() : new HashSet<>(committed);
    }

    private static VersionLocation locate(FileVersionDto v) {
        return new VersionLocation(v.versionId().toString(), blockKeys(v.ownerId(), v.hashes()));
    }

    private static List<String> blockKeys(UUID ownerId, List<String> hashes) {
        return hashes.stream().map(hash -> Blocks.key(ownerId, hash)).toList();
    }

    private FileVersionDto firstOrThrow(List<FileVersionDto> versions) {
        if (versions == null || versions.isEmpty()) {
            throw new BusinessException(StorageExceptionCase.FILE_NOT_FOUND_IN_STORAGE);
        }
        return versions.get(0);
    }
}
