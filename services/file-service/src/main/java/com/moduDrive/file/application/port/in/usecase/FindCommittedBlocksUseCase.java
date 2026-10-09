package com.moduDrive.file.application.port.in.usecase;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface FindCommittedBlocksUseCase {

    /** Which of these uploaded blocks were committed — storage-service asks before dropping an upload
     * that sat uncommitted past its window. A committed block is file-service's to delete. */
    Set<String> findCommittedBlocks(UUID ownerId, List<String> hashes);
}
