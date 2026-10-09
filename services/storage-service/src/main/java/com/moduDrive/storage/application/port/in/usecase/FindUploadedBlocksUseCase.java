package com.moduDrive.storage.application.port.in.usecase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FindUploadedBlocksUseCase {

    /** Uploaded, not-yet-expired blocks among {@code hashes} (hash → raw size) — file-service asks
     * when a commit names blocks it has no row for. */
    Map<String, Integer> findUploadedBlocks(UUID ownerId, List<String> hashes);
}
