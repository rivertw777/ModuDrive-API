package com.moduDrive.storage.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface FindUploadedBlocksPort {

    /** The blocks among {@code hashes} in S3 with the SHA-256 their hash names (hash → size). */
    Map<String, Integer> findUploaded(UUID ownerId, Collection<String> hashes);
}
