package com.moduDrive.storage.application.port.in.usecase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FindUploadedBlocksUseCase {

    /** Blocks among {@code hashes} that landed in S3 within the last 24h, intact (hash → size) — file-service asks
     * when a commit names blocks it has no row for. */
    Map<String, Integer> findUploadedBlocks(UUID ownerId, List<String> hashes);
}
