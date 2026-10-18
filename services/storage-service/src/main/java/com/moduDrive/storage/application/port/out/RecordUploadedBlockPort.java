package com.moduDrive.storage.application.port.out;

import java.util.Collection;
import java.util.UUID;

public interface RecordUploadedBlockPort {

    /** Puts the blocks on the uncommitted-upload sweep's schedule. Called before their upload URLs are
     * signed: a block uploaded but never scheduled would stay in S3 forever, while a scheduled one
     * that never got uploaded is only a no-op delete for the sweep. Scheduling a block again moves it
     * to now, so a re-upload is not stale. */
    void scheduleSweep(UUID ownerId, Collection<String> hashes);

    /** Counts {@code count} upload URLs against the owner's {@code Blocks.UPLOAD_TTL} window, all or
     * none; false if they'd take it past {@code limit}. Uncommitted blocks count toward no quota, so
     * without this one user could fill S3 and Redis with blocks nothing will ever commit. */
    boolean tryCountUploads(UUID ownerId, int count, int limit);
}
