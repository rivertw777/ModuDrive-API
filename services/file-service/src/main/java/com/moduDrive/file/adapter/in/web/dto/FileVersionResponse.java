package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;
import java.util.UUID;

/** {@code hashes} with {@code ownerId} locate the blocks: {@code blocks/{ownerId}/{hash}}, in order. */
public record FileVersionResponse(
        UUID versionId,
        UUID fileId,
        Long fileSize,
        UUID ownerId,
        List<String> hashes
) {
    public static FileVersionResponse from(FileVersion version) {
        return new FileVersionResponse(
                version.getId(), version.getFileId(),
                version.getFileSize(), version.getOwnerId(), version.getHashes()
        );
    }
}
