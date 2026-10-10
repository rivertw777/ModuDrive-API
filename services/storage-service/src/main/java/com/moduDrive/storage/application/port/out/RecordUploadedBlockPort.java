package com.moduDrive.storage.application.port.out;

import java.util.UUID;

public interface RecordUploadedBlockPort {

    /** Puts the block on the uncommitted-upload sweep's schedule. Called before the block goes to S3:
     * a block stored but never scheduled would stay in S3 forever, while a scheduled one that never
     * got stored is only a no-op delete for the sweep. */
    void scheduleSweep(UUID ownerId, String hash);

    /** Remembers for {@code Blocks.UPLOAD_TTL} that the block reached S3 with this raw size, so a
     * commit can use it — only after the block is in S3, or a commit would reference bytes that
     * aren't there; also moves it to now on the sweep's schedule. */
    void recordUploaded(UUID ownerId, String hash, int size);

    /** Counts one upload against the owner's {@code Blocks.UPLOAD_TTL} window; false once {@code limit}
     * uploads are already in it. Uncommitted blocks count toward no quota, so without this one user
     * could fill S3 and Redis with blocks nothing will ever commit. */
    boolean tryCountUpload(UUID ownerId, int limit);
}
