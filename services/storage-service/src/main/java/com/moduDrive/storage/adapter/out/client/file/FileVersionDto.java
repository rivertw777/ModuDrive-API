package com.moduDrive.storage.adapter.out.client.file;

import java.util.List;
import java.util.UUID;

record FileVersionDto(
        UUID versionId,
        UUID fileId,
        Long fileSize,
        UUID ownerId,
        List<String> hashes
) {
}
