package com.moduDrive.storage.adapter.out.client.file;

import java.util.List;
import java.util.UUID;

record FindCommittedBlocksRequest(UUID ownerId, List<String> hashes) {
}
