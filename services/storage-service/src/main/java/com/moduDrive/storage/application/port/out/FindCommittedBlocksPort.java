package com.moduDrive.storage.application.port.out;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface FindCommittedBlocksPort {

    /** Which of {@code hashes} file-service has committed — those are file-service's to delete. */
    Set<String> findCommitted(UUID ownerId, List<String> hashes);
}
