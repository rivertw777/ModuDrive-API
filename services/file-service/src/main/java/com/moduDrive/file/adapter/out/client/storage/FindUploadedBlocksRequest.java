package com.moduDrive.file.adapter.out.client.storage;

import java.util.List;
import java.util.UUID;

record FindUploadedBlocksRequest(UUID ownerId, List<String> hashes) {
}
