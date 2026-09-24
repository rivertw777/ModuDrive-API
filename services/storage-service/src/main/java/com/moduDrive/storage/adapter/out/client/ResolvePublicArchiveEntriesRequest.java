package com.moduDrive.storage.adapter.out.client;

import java.util.List;
import java.util.UUID;

record ResolvePublicArchiveEntriesRequest(String key, List<UUID> fileIds) {
}
