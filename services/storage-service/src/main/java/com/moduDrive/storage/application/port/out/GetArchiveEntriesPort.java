package com.moduDrive.storage.application.port.out;

import java.util.List;
import java.util.UUID;

public interface GetArchiveEntriesPort {

    /** file-service checks access to every picked item and lays out the zip (folders expanded,
     * duplicate top-level names numbered). */
    List<ArchiveEntry> getArchiveEntries(ArchiveRequest request);

    /** {@code path} is the path inside the zip; a directory ends in {@code /} and has no blocks.
     * {@code versionKey}/{@code blockKeys} as in {@link GetFileVersionPort.VersionLocation}. */
    record ArchiveEntry(String path, UUID fileId, String versionKey, List<String> blockKeys, long fileSize) {

        public boolean isDirectory() {
            return blockKeys == null;
        }
    }
}
