package com.moduDrive.storage.application.port.out;

import java.util.List;
import java.util.UUID;

public interface GetFileVersionPort {

    /** Also records a file-access (moves the file to the top of the user's "recent"): every caller is an
     * inline preview — an "open", as Google Drive counts it; downloads go through the archive flow. */
    VersionLocation getLatestVersion(UUID fileId, UUID userId);

    /** Link lookup for anonymous visitors: {@code fileId} identifies the entry (the shared file
     * or one nested under a shared folder) and {@code key} authorizes the read, so there is no
     * caller id to pass along. */
    VersionLocation getPublicVersion(String fileId, String key);

    /** {@code versionKey} identifies the version (the download quota counts per version);
     * {@code blockKeys} are its blocks' S3 keys in order; {@code fileSize} is its length in bytes
     * (0 when file-service didn't say). */
    record VersionLocation(String versionKey, List<String> blockKeys, long fileSize) {}
}
