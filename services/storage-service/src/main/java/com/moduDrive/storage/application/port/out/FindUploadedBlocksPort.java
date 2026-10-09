package com.moduDrive.storage.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface FindUploadedBlocksPort {

    /** The blocks among {@code hashes} uploaded within {@code Blocks.UPLOAD_TTL} (hash → size). */
    Map<String, Integer> findUploaded(UUID ownerId, Collection<String> hashes);
}
