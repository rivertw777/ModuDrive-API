package com.moduDrive.storage.application.port.out;

public interface DeleteBlocksPort {

    /** Deletes every block object under this version's S3 prefix. Deleting a key that's already
     * gone is a normal S3 no-op, not an error — safe to call again on a version that was already
     * (partially) cleaned up. */
    void deleteBlocks(String s3BasePath, int blockCount);
}
