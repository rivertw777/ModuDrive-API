package com.moduDrive.file.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface FindUploadedBlocksPort {

    /** Blocks among {@code hashes} that reached storage-service but aren't committed yet
     * (hash → size). An upload is remembered for 24 hours. */
    Map<String, Integer> findUploadedBlocks(UUID ownerId, Collection<String> hashes);
}
