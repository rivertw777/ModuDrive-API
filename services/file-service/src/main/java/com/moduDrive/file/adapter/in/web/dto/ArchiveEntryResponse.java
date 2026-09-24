package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.usecase.ArchiveEntry;

import java.util.UUID;

/** {@code fileId}/{@code s3Path}/{@code blockCount}/{@code fileSize} are null for a directory. */
public record ArchiveEntryResponse(
        String path,
        UUID fileId,
        String s3Path,
        Integer blockCount,
        Long fileSize
) {
    public static ArchiveEntryResponse from(ArchiveEntry entry) {
        if (entry.isDirectory()) {
            return new ArchiveEntryResponse(entry.path(), null, null, null, null);
        }
        var v = entry.version();
        return new ArchiveEntryResponse(entry.path(), v.getFileId(), v.getS3Path(), v.getBlockCount(), v.getFileSize());
    }
}
