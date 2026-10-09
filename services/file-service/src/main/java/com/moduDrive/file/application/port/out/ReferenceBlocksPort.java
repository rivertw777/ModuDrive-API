package com.moduDrive.file.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface ReferenceBlocksPort {

    /** Adds {@code countByHash} references to each of the owner's blocks, creating the row (with
     * its size from {@code sizeByHash}) for a block committed for the first time. */
    void referenceBlocks(UUID ownerId, Map<String, Integer> sizeByHash, Map<String, Integer> countByHash);
}
