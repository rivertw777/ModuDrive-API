package com.moduDrive.storage.adapter.out.client.file;

import java.util.UUID;

record FileVersionDto(
        UUID versionId,
        UUID fileId,
        Long fileSize,
        int blockCount,
        String s3Path
) {
}
