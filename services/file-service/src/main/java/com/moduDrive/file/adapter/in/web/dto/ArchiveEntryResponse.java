package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.usecase.ArchiveEntry;

import java.util.List;
import java.util.UUID;

/** Everything but {@code path} is null for a directory. */
public record ArchiveEntryResponse(
        String path,
        UUID fileId,
        UUID versionId,
        UUID ownerId,
        List<String> hashes,
        Long fileSize
) {
    public static ArchiveEntryResponse from(ArchiveEntry entry) {
        if (entry.isDirectory()) {
            return new ArchiveEntryResponse(entry.path(), null, null, null, null, null);
        }
        var v = entry.version();
        return new ArchiveEntryResponse(entry.path(), v.getFileId(), v.getId(), v.getOwnerId(), v.getHashes(),
                v.getFileSize());
    }
}
