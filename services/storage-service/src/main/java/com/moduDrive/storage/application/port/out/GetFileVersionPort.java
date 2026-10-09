package com.moduDrive.storage.application.port.out;

import java.util.List;
import java.util.UUID;

public interface GetFileVersionPort {

    /** {@code markAccessed} true records a file-access (moves the file to the top of the user's
     * "recent") — set it only for an inline preview/open, not a plain download, to match Google
     * Drive's behaviour. */
    VersionLocation getLatestVersion(UUID fileId, UUID userId, boolean markAccessed);

    /** Link lookup for anonymous visitors: {@code fileId} identifies the entry (the shared file
     * or one nested under a shared folder) and {@code key} authorizes the read, so there is no
     * caller id to pass along. */
    VersionLocation getPublicVersion(String fileId, String key);

    /** {@code versionKey} identifies the version (the download quota counts per version);
     * {@code blockKeys} are its blocks' S3 keys in order. */
    record VersionLocation(String versionKey, List<String> blockKeys) {}
}
