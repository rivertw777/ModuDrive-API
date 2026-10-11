package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.FileVersion;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReleaseBlocksPort {

    /** Drops the references these versions hold. Call it before their rows are deleted, in the same
     * transaction. A block left with none starts its grace period; the sweep deletes it later. */
    void releaseBlocks(List<FileVersion> versions);

    /** {@link #releaseBlocks} for every version of these files — a purge, which drops them all.
     * One statement however many files and blocks; nothing is loaded. */
    void releaseBlocksOfFiles(Collection<UUID> fileIds);
}
