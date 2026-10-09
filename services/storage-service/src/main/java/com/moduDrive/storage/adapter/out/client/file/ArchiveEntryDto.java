package com.moduDrive.storage.adapter.out.client.file;

import java.util.List;
import java.util.UUID;

/** Everything but {@code path} is null for a directory. */
record ArchiveEntryDto(String path, UUID fileId, UUID versionId, UUID ownerId, List<String> hashes, Long fileSize) {
}
