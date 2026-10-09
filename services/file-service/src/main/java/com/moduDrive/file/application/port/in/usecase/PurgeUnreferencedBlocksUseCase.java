package com.moduDrive.file.application.port.in.usecase;

public interface PurgeUnreferencedBlocksUseCase {

    /** Drops one batch of blocks nothing has referenced for the grace period, and asks
     * storage-service to delete them. Returns how many it claimed — a full batch means there may
     * be more. */
    int BATCH_SIZE = 500;

    int purgeUnreferencedBlocks();
}
