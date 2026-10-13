package com.moduDrive.storage.application.port.out;

import java.util.UUID;

public interface RecordUploadedBlockPort {

    /** Puts the block on the uncommitted-upload sweep's schedule. Called before its upload URL is
     * signed: a block uploaded but never scheduled would stay in S3 forever, while a scheduled one
     * that never got uploaded is only a no-op delete for the sweep. Scheduling a block again moves it
     * to now, so a re-upload is not stale. */
    void scheduleSweep(UUID ownerId, String hash);

    /** Counts one upload URL against the owner's {@code Blocks.UPLOAD_TTL} window; false once
     * {@code limit} are already in it. Uncommitted blocks count toward no quota, so without this one
     * user could fill S3 and Redis with blocks nothing will ever commit. */
    boolean tryCountUpload(UUID ownerId, int limit);
}
