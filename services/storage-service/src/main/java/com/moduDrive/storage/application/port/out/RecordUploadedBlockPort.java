package com.moduDrive.storage.application.port.out;

import java.util.UUID;

public interface RecordUploadedBlockPort {

    /** Remembers for {@code Blocks.UPLOAD_TTL} that the block reached S3 with this raw size, so a
     * commit can use it; also schedules it for the uncommitted-upload sweep. */
    void recordUploaded(UUID ownerId, String hash, int size);

    /** Counts one upload against the owner's {@code Blocks.UPLOAD_TTL} window; false once {@code limit}
     * uploads are already in it. Uncommitted blocks count toward no quota, so without this one user
     * could fill S3 and the Redis shared with sessions with blocks nothing will ever commit. */
    boolean tryCountUpload(UUID ownerId, int limit);
}
