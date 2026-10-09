package com.moduDrive.file.application.port.out;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface FindCommittedBlocksPort {

    /** Which of {@code hashes} the owner has a committed block row for, referenced or not. */
    Set<String> findCommittedHashes(UUID ownerId, Collection<String> hashes);
}
