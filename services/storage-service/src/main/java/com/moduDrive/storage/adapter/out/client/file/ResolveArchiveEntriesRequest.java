package com.moduDrive.storage.adapter.out.client.file;

import java.util.List;
import java.util.UUID;

record ResolveArchiveEntriesRequest(UUID userId, List<UUID> fileIds) {
}
