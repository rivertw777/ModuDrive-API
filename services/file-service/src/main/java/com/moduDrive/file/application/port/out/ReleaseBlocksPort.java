package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface ReleaseBlocksPort {

    /** Drops the references these versions hold. Call it before their rows are deleted, in the same
     * transaction. A block left with none starts its grace period; the sweep deletes it later. */
    void releaseBlocks(List<FileVersion> versions);
}
