package com.moduDrive.file.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface LockCommittedBlocksPort {

    /** The owner's committed blocks among {@code hashes} (hash → size), locked until the
     * transaction ends — so the unreferenced-block sweep can't delete one this commit is about to
     * reference. A hash with no row isn't committed; it may still be uploaded (see
     * {@link FindUploadedBlocksPort}). */
    Map<String, Integer> lockCommittedBlocks(UUID ownerId, Collection<String> hashes);
}
