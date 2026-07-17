package com.moduDrive.storage.adapter.out.client;

import java.util.UUID;

public record FileVersionDto(
        UUID versionId,
        UUID fileId,
        Long fileSize,
        int blockCount,
        String s3Path
) {
}
