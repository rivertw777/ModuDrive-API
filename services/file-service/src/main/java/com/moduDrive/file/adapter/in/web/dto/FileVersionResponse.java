package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.FileVersion;

import java.util.UUID;

public record FileVersionResponse(
        UUID versionId,
        UUID fileId,
        Long fileSize,
        int blockCount,
        String s3Path
) {
    public static FileVersionResponse from(FileVersion version) {
        return new FileVersionResponse(
                version.getId(), version.getFileId(),
                version.getFileSize(), version.getBlockCount(), version.getS3Path()
        );
    }
}
