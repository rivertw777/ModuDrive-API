package com.moduDrive.storage.application.port.out;

import java.util.List;
import java.util.UUID;

public interface GetArchiveEntriesPort {

    /** file-service checks access to every picked item and lays out the zip (folders expanded,
     * duplicate top-level names numbered). */
    List<ArchiveEntry> getArchiveEntries(ArchiveRequest request);

    /** {@code path} is the path inside the zip; a directory ends in {@code /} and has no
     * {@code s3Path}. */
    record ArchiveEntry(String path, UUID fileId, String s3Path, int blockCount, long fileSize) {

        public boolean isDirectory() {
            return s3Path == null;
        }
    }
}
